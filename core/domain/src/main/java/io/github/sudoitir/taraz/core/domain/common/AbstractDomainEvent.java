package io.github.sudoitir.taraz.core.domain.common;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Builder-built base for domain events. Concrete events are created only through their package's factory
 * (ADR-0005), so the builders never leak into general use.
 */
public abstract class AbstractDomainEvent implements DomainEvent {

    private final String eventType;
    private final Instant occurredAt;
    private final @Nullable String transactionId;

    protected AbstractDomainEvent(AbstractBuilder<?> builder) {
        this.eventType = Objects.requireNonNull(builder.eventType, "eventType");
        this.occurredAt = Objects.requireNonNull(builder.occurredAt, "occurredAt");
        this.transactionId = builder.transactionId;
    }

    @Override
    public final String eventType() {
        return eventType;
    }

    @Override
    public final Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public final @Nullable String transactionId() {
        return transactionId;
    }

    @Override
    public String toString() {
        return eventType + "@" + occurredAt;
    }

    public abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {
        private @Nullable String eventType;
        private @Nullable Instant occurredAt;
        private @Nullable String transactionId;

        public final T eventType(String eventType) {
            this.eventType = eventType;
            return self();
        }

        public final T occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return self();
        }

        public final T transactionId(@Nullable String transactionId) {
            this.transactionId = transactionId;
            return self();
        }

        protected abstract T self();
    }
}
