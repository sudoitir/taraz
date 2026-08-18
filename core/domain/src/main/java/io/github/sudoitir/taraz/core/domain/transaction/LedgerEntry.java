package io.github.sudoitir.taraz.core.domain.transaction;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.AbstractEntity;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One leg of a double-entry transaction (ADR-0037): account, direction, amount. Immutable child entity of
 * {@link Transaction} — the reason {@link AbstractEntity} exists.
 */
public final class LedgerEntry extends AbstractEntity<EntryId> {

    private final AccountId accountId;
    private final EntryDirection direction;
    private final Money amount;

    private LedgerEntry(Builder builder) {
        super(Objects.requireNonNull(builder.id, "id"));
        this.accountId = Objects.requireNonNull(builder.accountId, "accountId");
        this.direction = Objects.requireNonNull(builder.direction, "direction");
        this.amount = Objects.requireNonNull(builder.amount, "amount");
    }

    public static Builder builder() {
        return new Builder();
    }

    public AccountId accountId() {
        return accountId;
    }

    public EntryDirection direction() {
        return direction;
    }

    public Money amount() {
        return amount;
    }

    /** The signed effect of this leg on its account's balance. */
    public Money signedAmount() {
        return direction == EntryDirection.CREDIT ? amount : Money.ZERO.signedMinus(amount);
    }

    public static final class Builder {
        private @Nullable EntryId id;
        private @Nullable AccountId accountId;
        private @Nullable EntryDirection direction;
        private @Nullable Money amount;

        private Builder() {}

        public Builder id(EntryId id) {
            this.id = id;
            return this;
        }

        public Builder accountId(AccountId accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder direction(EntryDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        /** All failures are programmer errors (missing required field); domain rules live on {@link Transaction}. */
        public LedgerEntry build() {
            return new LedgerEntry(this);
        }
    }
}
