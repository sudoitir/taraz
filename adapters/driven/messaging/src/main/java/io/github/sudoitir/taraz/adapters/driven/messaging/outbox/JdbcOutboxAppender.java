package io.github.sudoitir.taraz.adapters.driven.messaging.outbox;

import io.github.sudoitir.taraz.adapters.driven.messaging.contract.IntegrationEventEnvelope;
import io.github.sudoitir.taraz.adapters.driven.messaging.correlation.MessagingCorrelation;
import io.github.sudoitir.taraz.core.application.ports.outbound.OutboxAppender;
import io.github.sudoitir.taraz.core.domain.common.DomainEvent;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR-0010/0049/0050: appends the {@code outbox} row inside the caller's existing transaction.
 *
 * <p>This adapter is deliberately built on {@code JdbcClient}, never JPA — {@code messaging} carries
 * no {@code spring-boot-starter-data-jpa} dependency (ADR-0049). Enlistment is not a dual write: Boot's
 * {@code JpaTransactionManager} binds the active JPA transaction's {@code Connection} as a
 * {@code ConnectionHolder} on the shared {@code DataSource}, and {@code JdbcClient} on that same
 * {@code DataSource} resolves the identical physical connection via {@code DataSourceUtils}. The
 * INSERT below commits or rolls back with the account/transaction writes as one atomic unit.
 *
 * <p>The row stores the <em>final serialized wire bytes</em> at append time (ADR-0050) — the polling
 * publisher later copies them to Kafka verbatim, so it needs no knowledge of the event contract and
 * cannot drift from what was actually committed.
 */
@Component
public final class JdbcOutboxAppender implements OutboxAppender {

    private static final String INSERT = """
            INSERT INTO outbox (occurred_at, created_at, next_attempt_at, attempts, id,
                                 aggregate_type, event_type, event_version, topic, partition_key,
                                 aggregate_id, transaction_id, correlation_id, payload)
            VALUES (:occurredAt, :createdAt, :nextAttemptAt, 0, :id,
                    :aggregateType, :eventType, :eventVersion, :topic, :partitionKey,
                    :aggregateId, :transactionId, :correlationId, CAST(:payload AS jsonb))
            """;

    private final JdbcClient jdbc;
    private final IntegrationEventFactory factory;
    private final IdGenerator ids;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcOutboxAppender(JdbcClient jdbc, IntegrationEventFactory factory, IdGenerator ids, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void append(List<DomainEvent> events) {
        Instant now = clock.instant();
        @Nullable
        String correlationId = MessagingCorrelation.currentCorrelationId().orElse(null);

        for (DomainEvent event : events) {
            UUID eventId = ids.newId();
            IntegrationEventEnvelope envelope = factory.toIntegrationEvent(event, eventId.toString(), correlationId);
            String topic = IntegrationEventFactory.topicFor(envelope.aggregateType());

            // PgJDBC's setObject cannot infer a SQL type for a raw java.time.Instant (unlike
            // Hibernate's own type system, which handles it directly) — plain JdbcClient binding needs
            // an explicit java.sql.Timestamp.
            jdbc.sql(INSERT)
                    .param("occurredAt", Timestamp.from(event.occurredAt()))
                    .param("createdAt", Timestamp.from(now))
                    .param("nextAttemptAt", Timestamp.from(now))
                    .param("id", eventId)
                    .param("aggregateType", envelope.aggregateType())
                    .param("eventType", envelope.eventType())
                    .param("eventVersion", envelope.eventVersion())
                    .param("topic", topic)
                    .param("partitionKey", envelope.aggregateId())
                    .param("aggregateId", envelope.aggregateId())
                    .param("transactionId", envelope.transactionId())
                    .param("correlationId", envelope.correlationId())
                    .param("payload", mapper.writeValueAsString(envelope))
                    .update();
        }
    }
}
