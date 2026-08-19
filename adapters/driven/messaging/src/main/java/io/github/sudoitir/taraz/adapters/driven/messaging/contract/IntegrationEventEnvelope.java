package io.github.sudoitir.taraz.adapters.driven.messaging.contract;

import org.jspecify.annotations.Nullable;

/**
 * The public wire contract for a published event (ADR-0009/0050) — built from a {@code DomainEvent} in
 * this adapter, kept deliberately separate from it so an internal domain-model change never breaks a
 * consumer. {@code eventId} is the outbox row's own id (UUIDv7) and is the consumer-side dedup key for
 * at-least-once delivery (ADR-0010/0027).
 *
 * @param eventVersion the payload record's own version (e.g. {@code "1"} for {@code AccountCreditedV1}) —
 *     distinct from the Kafka <em>topic</em> version suffix (ADR-0051's {@code taraz.account.v1}),
 *     which versions the topology, not any one payload shape
 * @param occurredAt ISO-8601 UTC (ADR-0009)
 */
public record IntegrationEventEnvelope(
        String eventId,
        String eventType,
        String eventVersion,
        String aggregateType,
        String aggregateId,
        @Nullable String transactionId,
        @Nullable String correlationId,
        String occurredAt,
        Object data) {}
