package io.github.sudoitir.taraz.core.domain.common;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Something that happened in the domain (ADR-0009). No {@code eventId}: the unique publication id belongs
 * to the outbox row / integration event built in the driven adapter, not to the domain.
 *
 * <p>{@link #transactionId()} is the raw client correlation id (String) so this package stays free of any
 * dependency on the transaction package; it is {@code null} only for events not caused by a transaction
 * (e.g. account opening).
 */
public interface DomainEvent {

    /** Stable dotted name, e.g. {@code "account.credited"}. */
    String eventType();

    /** Supplied by the caller — the domain never reads a clock (ADR-0005). */
    Instant occurredAt();

    @Nullable
    String transactionId();
}
