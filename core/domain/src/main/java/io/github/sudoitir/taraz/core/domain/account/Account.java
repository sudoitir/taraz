package io.github.sudoitir.taraz.core.domain.account;

import io.github.sudoitir.taraz.core.domain.account.event.AccountEvents;
import io.github.sudoitir.taraz.core.domain.account.spec.PositiveAmountSpecification;
import io.github.sudoitir.taraz.core.domain.account.spec.SufficientFundsSpecification;
import io.github.sudoitir.taraz.core.domain.common.AbstractAggregateRoot;
import io.github.sudoitir.taraz.core.domain.common.AbstractSpecification;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Minimal account aggregate: id + balance, no lifecycle. Every mutation evaluates its specifications
 * before touching the balance, so a rejected operation leaves the balance byte-identical — the
 * challenge's "operation must not be executed" is structural, not ordering discipline.
 *
 * <p>Mutators take a resolved {@link Instant}, not a {@code Clock}: the domain stays deterministic with
 * zero ambient state (ADR-0005); the clock lives in the application layer.
 *
 * <p>Not thread-safe (ADR-0026): each command loads a fresh instance under a row lock and discards it at
 * commit.
 */
public final class Account extends AbstractAggregateRoot<AccountId> {

    private static final NonNegativeBalance NON_NEGATIVE_BALANCE = new NonNegativeBalance();

    private Money balance;

    private Account(Builder builder) {
        super(Objects.requireNonNull(builder.id, "id"));
        this.balance = Objects.requireNonNull(builder.balance, "balance");
    }

    /** Opens a new account, recording {@code AccountOpened}. */
    public static Result<Account> open(AccountId id, Money initialBalance, Instant at) {
        return builder().id(id).balance(initialBalance).build().map(account -> {
            account.registerEvent(AccountEvents.opened(id, initialBalance, at));
            return account;
        });
    }

    /** Rehydrates from persisted state. Deliberately emits no events — replaying {@code AccountOpened} on every load would flood the outbox. */
    public static Result<Account> reconstitute(AccountId id, Money balance) {
        return builder().id(id).balance(balance).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Result<Money> credit(Money amount, TransactionId transactionId, Instant at) {
        return new PositiveAmountSpecification().check(amount).map(valid -> {
            balance = balance.plus(valid);
            registerEvent(AccountEvents.credited(id(), valid, balance, transactionId.value(), at));
            return balance;
        });
    }

    public Result<Money> debit(Money amount, TransactionId transactionId, Instant at) {
        return new PositiveAmountSpecification()
                .check(amount)
                .flatMap(valid -> new SufficientFundsSpecification(valid).check(this))
                .map(account -> {
                    // Guaranteed non-negative by the specification above; orElseThrow is a programmer assertion.
                    balance = balance.minus(amount).orElseThrow();
                    registerEvent(AccountEvents.debited(id(), amount, balance, transactionId.value(), at));
                    return balance;
                });
    }

    public Money balance() {
        return balance;
    }

    private static final class NonNegativeBalance extends AbstractSpecification<Builder> {
        @Override
        public boolean isSatisfiedBy(Builder builder) {
            return builder.balance != null && builder.balance.compareTo(Money.ZERO) >= 0;
        }

        @Override
        public DomainError violation(Builder builder) {
            return new DomainError(ErrorCode.NEGATIVE_BALANCE, "balance must not be negative: " + builder.balance);
        }
    }

    public static final class Builder {
        private @Nullable AccountId id;
        private @Nullable Money balance;

        private Builder() {}

        public Builder id(AccountId id) {
            this.id = id;
            return this;
        }

        public Builder balance(Money balance) {
            this.balance = balance;
            return this;
        }

        public Result<Account> build() {
            if (id == null || balance == null) {
                // Programmer error: a required field was never set — not a business outcome.
                throw new IllegalStateException("Account.Builder requires id and balance");
            }
            return NON_NEGATIVE_BALANCE.check(this).map(Account::new);
        }
    }
}
