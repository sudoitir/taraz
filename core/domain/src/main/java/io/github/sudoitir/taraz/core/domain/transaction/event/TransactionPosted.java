package io.github.sudoitir.taraz.core.domain.transaction.event;

import io.github.sudoitir.taraz.core.domain.common.AbstractDomainEvent;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionType;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A transaction was built and applied to its accounts. */
public final class TransactionPosted extends AbstractDomainEvent {

    public static final String EVENT_TYPE = "transaction.posted";

    private final TransactionType type;

    private TransactionPosted(Builder builder) {
        super(builder);
        this.type = Objects.requireNonNull(builder.type, "type");
    }

    public TransactionType type() {
        return type;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder extends AbstractBuilder<Builder> {
        private @Nullable TransactionType type;

        Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        TransactionPosted build() {
            return new TransactionPosted(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
