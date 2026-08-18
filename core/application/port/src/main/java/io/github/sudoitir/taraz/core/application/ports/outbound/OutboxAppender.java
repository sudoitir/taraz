package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.domain.common.DomainEvent;
import java.util.List;

/**
 * Appends recorded {@link DomainEvent}s to the outbox, in the same database transaction as the balance
 * change they describe (ADR-0010). The {@code DomainEvent} → {@code IntegrationEvent} mapping stays in
 * the messaging adapter (ADR-0009) — this port takes domain events, never a wire-format type.
 */
public interface OutboxAppender {
    void append(List<DomainEvent> events);
}
