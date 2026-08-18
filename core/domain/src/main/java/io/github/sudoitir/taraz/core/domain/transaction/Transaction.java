package io.github.sudoitir.taraz.core.domain.transaction;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.account.spec.PositiveAmountSpecification;
import io.github.sudoitir.taraz.core.domain.common.AbstractAggregateRoot;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.event.TransactionEvents;
import io.github.sudoitir.taraz.core.domain.transaction.spec.DistinctTransferAccountsSpecification;
import io.github.sudoitir.taraz.core.domain.transaction.spec.EntriesMatchTypeSpecification;
import io.github.sudoitir.taraz.core.domain.transaction.spec.UniformAmountSpecification;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Immutable double-entry transaction (ADR-0037). Fully immutable once built; the event list on the
 * aggregate-root base is the only mutable state, and it only ever receives one event at construction.
 *
 * <p>Leg shape by type: TRANSFER → exactly one debit + one credit on distinct accounts with equal
 * amounts (nets to zero); CREDIT/DEBIT → a single boundary leg (money enters/leaves the service). A
 * global clearing account was rejected — under ADR-0026's row locks it would serialize independent
 * accounts.
 *
 * <p>Compensation (ADR-0035): {@link #compensationOf} builds a new transaction with every leg reversed,
 * linked via {@code compensates}; the original is untouched.
 */
public final class Transaction extends AbstractAggregateRoot<TransactionId> {

    private final TransactionType type;
    private final TransactionStatus status;
    private final List<LedgerEntry> entries;
    private final Instant occurredAt;
    private final @Nullable TransactionId compensates;

    private Transaction(Builder builder) {
        super(Objects.requireNonNull(builder.id, "id"));
        this.type = Objects.requireNonNull(builder.type, "type");
        this.status = Objects.requireNonNull(builder.status, "status");
        this.entries = List.copyOf(Objects.requireNonNull(builder.entries, "entries"));
        this.occurredAt = Objects.requireNonNull(builder.occurredAt, "occurredAt");
        this.compensates = builder.compensates;
    }

    public static Result<Transaction> credit(
            TransactionId id, AccountId account, Money amount, Instant at, IdGenerator ids) {
        return builder()
                .id(id)
                .type(TransactionType.CREDIT)
                .entries(List.of(leg(ids, account, EntryDirection.CREDIT, amount)))
                .status(TransactionStatus.APPLIED)
                .occurredAt(at)
                .build()
                .map(tx -> {
                    tx.registerEvent(TransactionEvents.posted(id, TransactionType.CREDIT, at));
                    return tx;
                });
    }

    public static Result<Transaction> debit(
            TransactionId id, AccountId account, Money amount, Instant at, IdGenerator ids) {
        return builder()
                .id(id)
                .type(TransactionType.DEBIT)
                .entries(List.of(leg(ids, account, EntryDirection.DEBIT, amount)))
                .status(TransactionStatus.APPLIED)
                .occurredAt(at)
                .build()
                .map(tx -> {
                    tx.registerEvent(TransactionEvents.posted(id, TransactionType.DEBIT, at));
                    return tx;
                });
    }

    public static Result<Transaction> transfer(
            TransactionId id, AccountId source, AccountId destination, Money amount, Instant at, IdGenerator ids) {
        return builder()
                .id(id)
                .type(TransactionType.TRANSFER)
                .entries(List.of(
                        leg(ids, source, EntryDirection.DEBIT, amount),
                        leg(ids, destination, EntryDirection.CREDIT, amount)))
                .status(TransactionStatus.APPLIED)
                .occurredAt(at)
                .build()
                .map(tx -> {
                    tx.registerEvent(TransactionEvents.posted(id, TransactionType.TRANSFER, at));
                    return tx;
                });
    }

    /**
     * Reverses every leg of an {@code APPLIED} transaction into a new transaction with its own id
     * (ADR-0035). One implementation covers all three operation types and is structurally incapable of
     * producing an unbalanced reversal.
     */
    public static Result<Transaction> compensationOf(
            Transaction original, TransactionId newId, Instant at, IdGenerator ids) {
        if (original.status() != TransactionStatus.APPLIED) {
            return Result.failure(
                    ErrorCode.COMPENSATION_TARGET_NOT_APPLIED,
                    "only APPLIED transactions can be compensated: " + original.id());
        }
        List<LedgerEntry> reversed = original.entries().stream()
                .map(e -> leg(ids, e.accountId(), e.direction().reversed(), e.amount()))
                .toList();
        // ADR-0035: credit ↔ debit معکوس — the compensation takes the reversed type; transfers stay TRANSFER.
        TransactionType reversedType =
                switch (original.type()) {
                    case CREDIT -> TransactionType.DEBIT;
                    case DEBIT -> TransactionType.CREDIT;
                    case TRANSFER -> TransactionType.TRANSFER;
                };
        return builder()
                .id(newId)
                .type(reversedType)
                .entries(reversed)
                .status(TransactionStatus.APPLIED)
                .occurredAt(at)
                .compensates(original.id())
                .build()
                .map(tx -> {
                    tx.registerEvent(TransactionEvents.compensated(newId, original.id(), at));
                    return tx;
                });
    }

    private static LedgerEntry leg(IdGenerator ids, AccountId account, EntryDirection direction, Money amount) {
        return LedgerEntry.builder()
                .id(new EntryId(ids.newId()))
                .accountId(account)
                .direction(direction)
                .amount(amount)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public TransactionType type() {
        return type;
    }

    public TransactionStatus status() {
        return status;
    }

    public List<LedgerEntry> entries() {
        return entries;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Optional<TransactionId> compensates() {
        return Optional.ofNullable(compensates);
    }

    /** Signed net effect of this transaction's legs on the given account (zero if not a party). */
    public Money netEffectOn(AccountId accountId) {
        Money net = Money.ZERO;
        for (LedgerEntry entry : entries) {
            if (entry.accountId().equals(accountId)) {
                net = net.plus(entry.signedAmount());
            }
        }
        return net;
    }

    public static final class Builder {
        private @Nullable TransactionId id;
        private @Nullable TransactionType type;
        private @Nullable TransactionStatus status;
        private @Nullable List<LedgerEntry> entries;
        private @Nullable Instant occurredAt;
        private @Nullable TransactionId compensates;

        private Builder() {}

        public Builder id(TransactionId id) {
            this.id = id;
            return this;
        }

        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        public Builder status(TransactionStatus status) {
            this.status = status;
            return this;
        }

        public Builder entries(List<LedgerEntry> entries) {
            this.entries = entries;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder compensates(TransactionId compensates) {
            this.compensates = compensates;
            return this;
        }

        public Result<Transaction> build() {
            if (id == null || type == null || status == null || entries == null || occurredAt == null) {
                // Programmer error: a required field was never set — not a business outcome.
                throw new IllegalStateException(
                        "Transaction.Builder requires id, type, status, entries and occurredAt");
            }
            TransactionDraft draft = new TransactionDraft(type, entries);
            return new EntriesMatchTypeSpecification()
                    .check(draft)
                    .flatMap(new UniformAmountSpecification()::check)
                    .flatMap(new DistinctTransferAccountsSpecification()::check)
                    .flatMap(Transaction::checkLegAmountsPositive)
                    .map(d -> new Transaction(this));
        }
    }

    private static Result<TransactionDraft> checkLegAmountsPositive(TransactionDraft draft) {
        PositiveAmountSpecification positive = new PositiveAmountSpecification();
        for (LedgerEntry entry : draft.entries()) {
            Result<Money> checked = positive.check(entry.amount());
            if (checked.isFailure()) {
                return Result.failure(checked.error().orElseThrow());
            }
        }
        return Result.success(draft);
    }
}
