package io.github.sudoitir.taraz.core.domain.account.event;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.AbstractDomainEvent;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** An account was opened with an initial balance. Not caused by a transaction — {@code transactionId()} is null. */
public final class AccountOpened extends AbstractDomainEvent {

    public static final String EVENT_TYPE = "account.opened";

    private final AccountId accountId;
    private final Money balance;

    private AccountOpened(Builder builder) {
        super(builder);
        this.accountId = Objects.requireNonNull(builder.accountId, "accountId");
        this.balance = Objects.requireNonNull(builder.balance, "balance");
    }

    public AccountId accountId() {
        return accountId;
    }

    public Money balance() {
        return balance;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder extends AbstractBuilder<Builder> {
        private @Nullable AccountId accountId;
        private @Nullable Money balance;

        Builder accountId(AccountId accountId) {
            this.accountId = accountId;
            return this;
        }

        Builder balance(Money balance) {
            this.balance = balance;
            return this;
        }

        AccountOpened build() {
            return new AccountOpened(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
