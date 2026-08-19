package io.github.sudoitir.taraz.adapters.driven.messaging.outbox;

import io.github.sudoitir.taraz.adapters.driven.messaging.correlation.MessagingCorrelation;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR-0010/0027/0055/0057: claims a batch of unpublished rows with {@code FOR UPDATE SKIP LOCKED},
 * sends each to Kafka, and marks success or schedules a backoff retry — all inside one DB transaction.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} alone already guarantees two pods never publish the <em>same</em>
 * row twice, but without {@code @SchedulerLock} every pod would still run its own poll every tick,
 * each paying a full claim query for rows the others already took. ShedLock (ADR-0057) lets exactly
 * one pod poll per tick, so the outbox scales cleanly with pod count instead of merely being safe
 * under it — this is what resolves ADR-0055's original single-instance-poller caveat.
 *
 * <p><b>Send-then-mark</b>, never mark-then-send: a crash between the two would otherwise lose the
 * event instead of merely risking a harmless duplicate under Kafka's idempotent producer.
 *
 * <p>{@code @DependsOn("liquibase")}: on a brand-new database, this bean's first scheduled tick can
 * otherwise race Liquibase's migration (observed under Testcontainers) — forcing Spring's bean
 * creation graph to build the {@code liquibase} bean first guarantees the {@code outbox} table exists
 * before this bean's {@code @Scheduled} infrastructure is registered at all.
 */
@Component
@DependsOn("liquibase")
public class OutboxPollingPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPollingPublisher.class);

    private static final String CLAIM = """
            SELECT id, topic, partition_key, event_type, event_version, correlation_id, payload, attempts
            FROM outbox
            WHERE published_at IS NULL AND next_attempt_at <= now()
            ORDER BY id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """;

    private static final String MARK_PUBLISHED =
            "UPDATE outbox SET published_at = now(), attempts = attempts + 1 WHERE id = :id";

    private static final String MARK_FAILED =
            "UPDATE outbox SET attempts = attempts + 1, next_attempt_at = :nextAttemptAt WHERE id = :id";

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, byte[]> kafka;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;

    public OutboxPollingPublisher(
            JdbcClient jdbc,
            KafkaTemplate<String, byte[]> kafka,
            PlatformTransactionManager txManager,
            OutboxProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.transactionTemplate = new TransactionTemplate(Objects.requireNonNull(txManager, "txManager"));
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    // initialDelay: @Scheduled tasks start once the context finishes refreshing, which is strictly
    // after Liquibase's synchronous migration bean — but starting at t=0 leaves no margin if startup
    // is ever slow (observed under Testcontainers: the first tick or two can race a still-settling
    // connection pool against a just-created table). One poll interval of headroom costs nothing.
    @Scheduled(
            initialDelayString = "${taraz.outbox.poll-interval:200ms}",
            fixedDelayString = "${taraz.outbox.poll-interval:200ms}")
    // lockAtMostFor: a ceiling far above a realistic batch (128 rows, 10s send timeout each in the
    // worst case) but short enough that a pod dying mid-poll doesn't strand the lock for long;
    // lockAtLeastFor: none — ticks are 200ms apart and idle ticks (empty batch) should never hold
    // the lock artificially, or a real pod's turn would be skipped for no reason.
    @SchedulerLock(name = "outbox-poll", lockAtMostFor = "30s")
    public void publishBatch() {
        LockAssert.assertLocked();
        transactionTemplate.executeWithoutResult(status -> {
            List<PendingRow> batch = jdbc.sql(CLAIM)
                    .param("batchSize", properties.batchSize())
                    .query(PendingRow.ROW_MAPPER)
                    .list();
            if (batch.isEmpty()) {
                return;
            }

            // Issue every send in the batch up front, then await each result — never await one row's
            // future before issuing the next row's send. Awaiting inside the loop (the original shape
            // here) would serialize the whole batch behind each row's own network round trip: with a
            // 10s send-timeout and a 128-row batch, one degraded row could stall the entire tick for
            // minutes even though KafkaTemplate#send is itself async and Kafka pipelines concurrent
            // in-flight sends fine (max.in.flight.requests.per.connection=5, ADR-0027/0055).
            List<PendingSend> sends =
                    batch.stream().map(row -> new PendingSend(row, send(row))).toList();

            int publishedCount = 0;
            for (PendingSend pending : sends) {
                if (awaitSend(pending)) {
                    jdbc.sql(MARK_PUBLISHED).param("id", pending.row().id()).update();
                    publishedCount++;
                } else {
                    markFailed(pending.row());
                }
            }
            log.debug("outbox batch: {} claimed, {} published", batch.size(), publishedCount);
        });
    }

    private boolean awaitSend(PendingSend pending) {
        PendingRow row = pending.row();
        String correlationId = row.correlationId();
        try (var ignored = correlationId == null
                ? null
                : MDC.putCloseable(MessagingCorrelation.CORRELATION_ID_MDC_KEY, correlationId)) {
            SendResult<String, byte[]> result =
                    pending.future().get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return result != null;
        } catch (Exception e) {
            log.warn("outbox publish failed for {} — will retry", row.id(), e);
            return false;
        }
    }

    private CompletableFuture<SendResult<String, byte[]>> send(PendingRow row) {
        ProducerRecord<String, byte[]> record =
                new ProducerRecord<>(row.topic(), null, row.partitionKey(), row.payload());
        record.headers().add("X-Event-Id", row.id().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("X-Event-Type", row.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add("X-Event-Version", row.eventVersion().getBytes(StandardCharsets.UTF_8));
        record.headers().add("content-type", "application/json".getBytes(StandardCharsets.UTF_8));
        // ADR-0056: kafka_correlationId is Spring Kafka's own convention (KafkaHeaders.CORRELATION_ID),
        // deliberately not the HTTP header's name.
        if (row.correlationId() != null) {
            record.headers()
                    .add(KafkaHeaders.CORRELATION_ID, row.correlationId().getBytes(StandardCharsets.UTF_8));
        }
        return kafka.send(record);
    }

    /** Exponential backoff to {@link OutboxProperties#backoffCeiling()} — a poison row cannot starve the queue. */
    private void markFailed(PendingRow row) {
        long delaySeconds = Math.min(
                1L << Math.min(row.attempts(), 20), properties.backoffCeiling().toSeconds());
        Instant nextAttempt = Instant.now().plusSeconds(Math.max(delaySeconds, 1));
        // PgJDBC can't infer a SQL type for a raw java.time.Instant via plain JdbcClient binding.
        jdbc.sql(MARK_FAILED)
                .param("id", row.id())
                .param("nextAttemptAt", java.sql.Timestamp.from(nextAttempt))
                .update();
        if (row.attempts() + 1 >= properties.maxAttempts()) {
            // Never auto-deleted (ADR-0055) — a financial event is never silently dropped; escalation
            // is via monitoring rows with attempts >= max-attempts.
            log.error(
                    "outbox row {} has failed {} attempts — requires investigation, still retrying",
                    row.id(),
                    row.attempts() + 1);
        }
    }

    // Array component is fine here: never compared/hashed, only read once immediately after the row mapper builds it.
    @SuppressWarnings("ArrayRecordComponent")
    private record PendingRow(
            UUID id,
            String topic,
            String partitionKey,
            String eventType,
            String eventVersion,
            @Nullable String correlationId,
            byte[] payload,
            int attempts) {

        static final RowMapper<PendingRow> ROW_MAPPER = (rs, rowNum) -> new PendingRow(
                rs.getObject("id", UUID.class),
                rs.getString("topic"),
                rs.getString("partition_key"),
                rs.getString("event_type"),
                rs.getString("event_version"),
                rs.getString("correlation_id"),
                rs.getString("payload").getBytes(StandardCharsets.UTF_8),
                rs.getInt("attempts"));
    }

    /** A send already issued to Kafka, paired with the row it came from, awaiting its result. */
    private record PendingSend(PendingRow row, CompletableFuture<SendResult<String, byte[]>> future) {}
}
