package io.github.sudoitir.taraz.core.domain.account.event;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.AbstractDomainEvent;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Money entered an account. Carries the resulting balance so a consumer never has to reconstruct it. */
public final class AccountCredited extends AbstractDomainEvent {

    public static final String EVENT_TYPE = "account.credited";

    private final AccountId accountId;
    private final Money amount;
    private final Money balanceAfter;

    private AccountCredited(Builder builder) {
        super(builder);
        this.accountId = Objects.requireNonNull(builder.accountId, "accountId");
        this.amount = Objects.requireNonNull(builder.amount, "amount");
        this.balanceAfter = Objects.requireNonNull(builder.balanceAfter, "balanceAfter");
    }

    public AccountId accountId() {
        return accountId;
    }

    public Money amount() {
        return amount;
    }

    public Money balanceAfter() {
        return balanceAfter;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder extends AbstractBuilder<Builder> {
        private @Nullable AccountId accountId;
        private @Nullable Money amount;
        private @Nullable Money balanceAfter;

        Builder accountId(AccountId accountId) {
            this.accountId = accountId;
            return this;
        }

        Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        Builder balanceAfter(Money balanceAfter) {
            this.balanceAfter = balanceAfter;
            return this;
        }

        AccountCredited build() {
            return new AccountCredited(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
