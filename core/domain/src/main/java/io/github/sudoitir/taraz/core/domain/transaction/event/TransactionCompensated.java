package io.github.sudoitir.taraz.core.domain.transaction.event;

import io.github.sudoitir.taraz.core.domain.common.AbstractDomainEvent;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A compensation transaction reversed an earlier one (ADR-0035); the original stays untouched. */
public final class TransactionCompensated extends AbstractDomainEvent {

    public static final String EVENT_TYPE = "transaction.compensated";

    private final TransactionId compensates;

    private TransactionCompensated(Builder builder) {
        super(builder);
        this.compensates = Objects.requireNonNull(builder.compensates, "compensates");
    }

    /** The transaction this compensation reverses. */
    public TransactionId compensates() {
        return compensates;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder extends AbstractBuilder<Builder> {
        private @Nullable TransactionId compensates;

        Builder compensates(TransactionId compensates) {
            this.compensates = compensates;
            return this;
        }

        TransactionCompensated build() {
            return new TransactionCompensated(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
