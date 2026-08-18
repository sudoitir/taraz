package io.github.sudoitir.taraz.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.service.credit.CreditHandler;
import io.github.sudoitir.taraz.core.application.service.debit.DebitHandler;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeAccountRepository;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeIdempotencyGate;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeOutboxAppender;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeProcessedTransactionStore;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeTransactionRepository;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeUnitOfWork;
import io.github.sudoitir.taraz.core.application.service.support.TestValidator;
import io.github.sudoitir.taraz.core.application.service.transfer.TransferHandler;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.service.PostingService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The challenge's concurrency invariants (`.claude/rules/challenge-concurrency.md`), proven against
 * {@link FakeAccountRepository}'s row-lock model: same-account operations serialize to a deterministic
 * result; independent accounts never wait on each other; ordered locking is deadlock-free even under
 * many concurrent bidirectional transfers between the same pair.
 *
 * <p>This proves handler <em>logic</em>. The real {@code SELECT ... FOR UPDATE} proof against PostgreSQL
 * needs Testcontainers and lands with the persistence change — stated in proposal.md's Non-goals.
 */
class ConcurrencySuiteTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final IdGenerator IDS = new UuidV7IdGenerator();
    private static final Clock CLOCK = Clock.fixed(AT, ZoneOffset.UTC);

    private static AccountId newAccountId() {
        return new AccountId(IDS.newId());
    }

    private record Fixture(
            FakeAccountRepository accounts,
            FakeUnitOfWork unitOfWork,
            CreditHandler credit,
            DebitHandler debit,
            TransferHandler transfer) {}

    private static Fixture newFixture() {
        FakeAccountRepository accounts = new FakeAccountRepository();
        FakeUnitOfWork unitOfWork = new FakeUnitOfWork(accounts);
        FakeTransactionRepository transactions = new FakeTransactionRepository();
        FakeProcessedTransactionStore processed = new FakeProcessedTransactionStore();
        FakeOutboxAppender outbox = new FakeOutboxAppender();
        var validator = TestValidator.commandValidator();
        var postingService = new PostingService(IDS);

        CreditHandler credit = new CreditHandler(
                postingService,
                CLOCK,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL));
        DebitHandler debit = new DebitHandler(
                postingService,
                CLOCK,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL));
        TransferHandler transfer = new TransferHandler(
                postingService,
                CLOCK,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL));
        return new Fixture(accounts, unitOfWork, credit, debit, transfer);
    }

    @Test
    void thousandConcurrentDebitsOnOneAccountLeaveTheExactExpectedBalance() throws Exception {
        Fixture f = newFixture();
        AccountId a = newAccountId();
        f.accounts.seed(a, Money.of(100_000).orElseThrow());
        int n = 1000;

        List<Result<CommandOutcome>> results =
                runConcurrently(n, i -> f.debit.handle(new DebitCommand(a.toString(), 100, "TX-DRAIN-" + i)));

        assertThat(results).allSatisfy(r -> assertThat(r.isSuccess()).isTrue());
        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.ZERO);
    }

    @Test
    void exactlyOneOfTwoConcurrentDebitsBeyondHalfBalanceSucceeds() throws Exception {
        Fixture f = newFixture();
        AccountId a = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());

        List<Result<CommandOutcome>> results =
                runConcurrently(2, i -> f.debit.handle(new DebitCommand(a.toString(), 700, "TX-RACE-" + i)));

        long successes = results.stream().filter(Result::isSuccess).count();
        long insufficientFunds = results.stream()
                .filter(Result::isFailure)
                .filter(r -> r.error().orElseThrow().code() == ErrorCode.INSUFFICIENT_FUNDS)
                .count();

        assertThat(successes).isEqualTo(1);
        assertThat(insufficientFunds).isEqualTo(1);
        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.of(300).orElseThrow());
    }

    @Test
    void operationsOnIndependentAccountsNeverWaitOnEachOther() throws Exception {
        Fixture f = newFixture();
        AccountId a = newAccountId();
        AccountId b = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());
        f.accounts.seed(b, Money.of(1000).orElseThrow());

        // Each thread locks a DIFFERENT account, then rendezvous at a 2-party barrier while still
        // holding its lock. If the accounts' locks were shared (serialized), the second thread could
        // never reach the barrier until the first released — and the first cannot release until the
        // barrier trips. A shared lock deadlocks this; independent locks complete well inside the
        // timeout.
        CyclicBarrier bothHoldingTheirLock = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Result<List<Account>>> holdLockThenRendezvous = () -> f.unitOfWork.inTransaction(() -> {
                Result<List<Account>> locked = f.accounts.lockAllInIdOrder(Set.of(a));
                awaitUnchecked(bothHoldingTheirLock);
                return locked;
            });
            Callable<Result<List<Account>>> other = () -> f.unitOfWork.inTransaction(() -> {
                Result<List<Account>> locked = f.accounts.lockAllInIdOrder(Set.of(b));
                awaitUnchecked(bothHoldingTheirLock);
                return locked;
            });

            Future<Result<List<Account>>> first = pool.submit(holdLockThenRendezvous);
            Future<Result<List<Account>>> second = pool.submit(other);

            assertThat(first.get(5, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS).isSuccess()).isTrue();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void manyConcurrentBidirectionalTransfersCompleteWithoutDeadlockAndConserveTheTotal() throws Exception {
        Fixture f = newFixture();
        AccountId a = newAccountId();
        AccountId b = newAccountId();
        f.accounts.seed(a, Money.of(100_000).orElseThrow());
        f.accounts.seed(b, Money.of(100_000).orElseThrow());
        int perDirection = 100;
        int n = perDirection * 2;

        List<Result<CommandOutcome>> results = runConcurrently(n, i -> {
            boolean aToB = i % 2 == 0;
            String txId = "TX-XFER-" + i;
            return aToB
                    ? f.transfer.handle(new TransferCommand(a.toString(), b.toString(), 1, txId))
                    : f.transfer.handle(new TransferCommand(b.toString(), a.toString(), 1, txId));
        });

        assertThat(results).allSatisfy(r -> assertThat(r.isSuccess()).isTrue());
        Money balanceA = Objects.requireNonNull(f.accounts.balanceOf(a));
        Money balanceB = Objects.requireNonNull(f.accounts.balanceOf(b));
        assertThat(balanceA.plus(balanceB)).isEqualTo(Money.of(200_000).orElseThrow());
    }

    private static void awaitUnchecked(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> List<T> runConcurrently(int n, java.util.function.IntFunction<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        try {
            List<Future<T>> futures = IntStream.range(0, n)
                    .<Callable<T>>mapToObj(i -> () -> {
                        barrier.await();
                        return task.apply(i);
                    })
                    .map(pool::submit)
                    .toList();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
