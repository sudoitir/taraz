package io.github.sudoitir.taraz.core.domain.service;

import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.spec.SufficientFundsSpecification;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.EntryDirection;
import io.github.sudoitir.taraz.core.domain.transaction.LedgerEntry;
import io.github.sudoitir.taraz.core.domain.transaction.Transaction;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The domain's single entry point for postings. Stateless (only the shared {@link IdGenerator}).
 *
 * <p><b>Every specification is evaluated before any aggregate is mutated</b>: the transaction is built
 * first (shape, amounts, distinct accounts), then funds are checked, and only then are accounts touched.
 * A failed operation therefore never leaves a half-applied in-memory state — "no partial operation"
 * holds by construction, with no rollback logic in the domain.
 *
 * <p>The application handler around this reduces to: lock rows in {@code accountId} order (ADR-0026) →
 * load accounts → call this service → persist accounts + transaction + outbox row in one DB transaction
 * (ADR-0018/0010) → publish {@code pullDomainEvents()}.
 */
public final class PostingService {

    private final IdGenerator ids;

    public PostingService(IdGenerator ids) {
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    public Result<PostingResult> credit(Account account, Money amount, TransactionId transactionId, Instant at) {
        return Transaction.credit(transactionId, account.id(), amount, at, ids)
                .flatMap(tx -> account.credit(amount, transactionId, at)
                        .map(balance -> new PostingResult(tx, List.of(account))));
    }

    public Result<PostingResult> debit(Account account, Money amount, TransactionId transactionId, Instant at) {
        return Transaction.debit(transactionId, account.id(), amount, at, ids)
                .flatMap(tx -> account.debit(amount, transactionId, at)
                        .map(balance -> new PostingResult(tx, List.of(account))));
    }

    public Result<PostingResult> transfer(
            Account source, Account destination, Money amount, TransactionId transactionId, Instant at) {
        return Transaction.transfer(transactionId, source.id(), destination.id(), amount, at, ids)
                // All rules evaluated before any mutation: same-account and amount shape came from the
                // build above; funds come here. After the check both mutations are guaranteed to succeed.
                .flatMap(tx -> new SufficientFundsSpecification(amount)
                        .check(source)
                        .flatMap(sourceChecked -> source.debit(amount, transactionId, at))
                        .flatMap(sourceBalance -> destination.credit(amount, transactionId, at))
                        .map(destinationBalance -> new PostingResult(tx, List.of(source, destination))));
    }

    public Result<PostingResult> compensate(
            Transaction original, List<Account> accounts, TransactionId transactionId, Instant at) {
        return Transaction.compensationOf(original, transactionId, at, ids)
                .flatMap(compensation -> checkCompensationFunds(compensation, accounts))
                .map(compensation -> applyLegs(compensation, accounts));
    }

    /** Pre-checks that no leg of the compensation would drive a balance negative, before mutating anything. */
    private static Result<Transaction> checkCompensationFunds(Transaction compensation, List<Account> accounts) {
        for (Account account : accounts) {
            Money net = compensation.netEffectOn(account.id());
            if (net.compareTo(Money.ZERO) < 0) {
                Result<Account> check = new SufficientFundsSpecification(Money.ZERO.signedMinus(net)).check(account);
                if (check.isFailure()) {
                    return Result.failure(check.error().orElseThrow());
                }
            }
        }
        return Result.success(compensation);
    }

    private static PostingResult applyLegs(Transaction compensation, List<Account> accounts) {
        List<Account> involved = accounts.stream()
                .filter(a -> compensation.entries().stream()
                        .anyMatch(e -> e.accountId().equals(a.id())))
                .toList();
        for (LedgerEntry entry : compensation.entries()) {
            Account account = involved.stream()
                    .filter(a -> a.id().equals(entry.accountId()))
                    .findFirst()
                    // Programmer error: the handler must supply every account the transaction touches.
                    .orElseThrow(() -> new IllegalStateException("missing account " + entry.accountId()));
            Result<Money> applied = entry.direction() == EntryDirection.CREDIT
                    ? account.credit(entry.amount(), compensation.id(), compensation.occurredAt())
                    : account.debit(entry.amount(), compensation.id(), compensation.occurredAt());
            // Guaranteed by checkCompensationFunds above; orElseThrow is a programmer assertion.
            applied.orElseThrow();
        }
        return new PostingResult(compensation, involved);
    }
}
