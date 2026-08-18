package io.github.sudoitir.taraz.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
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
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.service.PostingService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * A {@code transactionId} affects a balance exactly once — sequentially, concurrently, and even when
 * the advisory {@link FakeIdempotencyGate} never has an answer (design.md D7/D8). Each fixture is built
 * fresh per test so concurrent tests do not share state with sequential ones.
 */
class IdempotencySuiteTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final IdGenerator IDS = new UuidV7IdGenerator();
    private static final Clock CLOCK = Clock.fixed(AT, ZoneOffset.UTC);

    private record Fixture(
            FakeAccountRepository accounts,
            FakeTransactionRepository transactions,
            FakeProcessedTransactionStore processed,
            CreditHandler credit,
            DebitHandler debit,
            TransferHandler transfer) {}

    private static Fixture newFixture(FakeIdempotencyGate.Mode gateMode) {
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
                new FakeIdempotencyGate(gateMode));
        DebitHandler debit = new DebitHandler(
                postingService,
                CLOCK,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(gateMode));
        TransferHandler transfer = new TransferHandler(
                postingService,
                CLOCK,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(gateMode));
        return new Fixture(accounts, transactions, processed, credit, debit, transfer);
    }

    private static AccountId newAccountId() {
        return new AccountId(IDS.newId());
    }

    @Test
    void sequentialDuplicateCredit() {
        Fixture f = newFixture(FakeIdempotencyGate.Mode.NORMAL);
        AccountId a = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());

        CommandOutcome first =
                f.credit.handle(new CreditCommand(a.toString(), 100, "TX-1")).orElseThrow();
        CommandOutcome second =
                f.credit.handle(new CreditCommand(a.toString(), 100, "TX-1")).orElseThrow();
        CommandOutcome third =
                f.credit.handle(new CreditCommand(a.toString(), 100, "TX-1")).orElseThrow();

        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.of(1100).orElseThrow());
        assertThat(first.status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(second.status()).isEqualTo(OutcomeStatus.REPLAYED);
        assertThat(third.status()).isEqualTo(OutcomeStatus.REPLAYED);
        assertThat(second.balances()).isEqualTo(first.balances());
        assertThat(third.balances()).isEqualTo(first.balances());
    }

    @Test
    void sequentialDuplicateDebit() {
        Fixture f = newFixture(FakeIdempotencyGate.Mode.NORMAL);
        AccountId a = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());

        f.debit.handle(new DebitCommand(a.toString(), 100, "TX-2")).orElseThrow();
        f.debit.handle(new DebitCommand(a.toString(), 100, "TX-2")).orElseThrow();
        f.debit.handle(new DebitCommand(a.toString(), 100, "TX-2")).orElseThrow();

        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.of(900).orElseThrow());
    }

    @Test
    void sequentialDuplicateTransfer() {
        Fixture f = newFixture(FakeIdempotencyGate.Mode.NORMAL);
        AccountId a = newAccountId();
        AccountId b = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());
        f.accounts.seed(b, Money.of(500).orElseThrow());

        f.transfer
                .handle(new TransferCommand(a.toString(), b.toString(), 100, "TX-3"))
                .orElseThrow();
        f.transfer
                .handle(new TransferCommand(a.toString(), b.toString(), 100, "TX-3"))
                .orElseThrow();
        f.transfer
                .handle(new TransferCommand(a.toString(), b.toString(), 100, "TX-3"))
                .orElseThrow();

        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.of(900).orElseThrow());
        assertThat(f.accounts.balanceOf(b)).isEqualTo(Money.of(600).orElseThrow());
    }

    @Test
    void concurrentDuplicateCreditsChangeTheBalanceExactlyOnce() throws Exception {
        Fixture f = newFixture(FakeIdempotencyGate.Mode.NORMAL);
        AccountId a = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());
        int n = 50;

        List<Result<CommandOutcome>> results =
                runConcurrently(n, () -> f.credit.handle(new CreditCommand(a.toString(), 100, "TX-CONCURRENT")));

        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.of(1100).orElseThrow());
        assertThat(results).allSatisfy(r -> assertThat(r.isSuccess()).isTrue());
        long appliedCount = results.stream()
                .filter(r -> r.orElseThrow().status() == OutcomeStatus.APPLIED)
                .count();
        assertThat(appliedCount).isEqualTo(1);
        assertThat(f.transactions.savedCount()).isEqualTo(1);
        assertThat(f.processed.recordedCount()).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateTransfersMoveTheAmountExactlyOnce() throws Exception {
        Fixture f = newFixture(FakeIdempotencyGate.Mode.NORMAL);
        AccountId a = newAccountId();
        AccountId b = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());
        f.accounts.seed(b, Money.of(500).orElseThrow());
        int n = 50;

        List<Result<CommandOutcome>> results = runConcurrently(
                n, () -> f.transfer.handle(new TransferCommand(a.toString(), b.toString(), 300, "TX-CONCURRENT-XFER")));

        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.of(700).orElseThrow());
        assertThat(f.accounts.balanceOf(b)).isEqualTo(Money.of(800).orElseThrow());
        long appliedCount = results.stream()
                .filter(r -> r.orElseThrow().status() == OutcomeStatus.APPLIED)
                .count();
        assertThat(appliedCount).isEqualTo(1);
    }

    @Test
    void duplicateSurvivesAnAdvisoryGateThatNeverAnswers() {
        // D7: Unknown always falls through to the authoritative database path, so exactly-once holds
        // even though the gate itself never reports AlreadyApplied.
        Fixture f = newFixture(FakeIdempotencyGate.Mode.ALWAYS_UNKNOWN);
        AccountId a = newAccountId();
        f.accounts.seed(a, Money.of(1000).orElseThrow());

        CommandOutcome first =
                f.credit.handle(new CreditCommand(a.toString(), 100, "TX-4")).orElseThrow();
        CommandOutcome second =
                f.credit.handle(new CreditCommand(a.toString(), 100, "TX-4")).orElseThrow();

        assertThat(f.accounts.balanceOf(a)).isEqualTo(Money.of(1100).orElseThrow());
        assertThat(first.status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(second.status()).isEqualTo(OutcomeStatus.REPLAYED);
    }

    private static <T> List<T> runConcurrently(int n, Callable<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        try {
            List<Future<T>> futures = IntStream.range(0, n)
                    .<Callable<T>>mapToObj(i -> () -> {
                        barrier.await();
                        return task.call();
                    })
                    .map(pool::submit)
                    .toList();
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
