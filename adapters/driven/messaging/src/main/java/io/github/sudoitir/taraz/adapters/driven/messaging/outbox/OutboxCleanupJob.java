package io.github.sudoitir.taraz.adapters.driven.messaging.outbox;

import java.time.Instant;
import java.util.Objects;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ADR-0019/0055/0057: periodic retention cleanup for the hot {@code outbox} table. Deletes in bounded
 * chunks, each its own small transaction — never one long-running transaction against a table this
 * churny — until a chunk comes back empty. {@code @SchedulerLock} (ADR-0057) keeps this to one pod at
 * a time; without it every pod in a horizontally-scaled deployment would redundantly run the same
 * chunked delete loop against rows the others already removed. {@code @DependsOn("liquibase")}: see
 * {@link OutboxPollingPublisher}'s javadoc — same startup-ordering guarantee, same reason.
 */
@Component
@DependsOn("liquibase")
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);
    private static final int CHUNK_SIZE = 10_000;

    private static final String DELETE_CHUNK = """
            DELETE FROM outbox WHERE id IN (
                SELECT id FROM outbox WHERE published_at IS NOT NULL AND published_at < :cutoff
                ORDER BY published_at LIMIT :chunkSize
            )
            """;

    private final JdbcClient jdbc;
    private final OutboxProperties properties;

    public OutboxCleanupJob(JdbcClient jdbc, OutboxProperties properties) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Scheduled(cron = "${taraz.outbox.cleanup-cron:0 15 3 * * *}")
    // A daily job clearing a potentially large backlog can legitimately run long — 10m is generous
    // headroom, not an expected duration.
    @SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "10m")
    public void cleanupPublishedRows() {
        LockAssert.assertLocked();
        Instant cutoff = Instant.now().minus(properties.retention());
        int totalDeleted = 0;
        int deletedThisChunk;
        do {
            // PgJDBC can't infer a SQL type for a raw java.time.Instant via plain JdbcClient binding.
            deletedThisChunk = jdbc.sql(DELETE_CHUNK)
                    .param("cutoff", java.sql.Timestamp.from(cutoff))
                    .param("chunkSize", CHUNK_SIZE)
                    .update();
            totalDeleted += deletedThisChunk;
        } while (deletedThisChunk == CHUNK_SIZE);

        if (totalDeleted > 0) {
            log.info("outbox cleanup: deleted {} published rows older than {}", totalDeleted, properties.retention());
        }
    }
}
