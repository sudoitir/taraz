package io.github.sudoitir.taraz.adapters.driven.messaging.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ADR-0055: outbox delivery policy — batch size, send timeout, backoff ceiling, the alert-not-delete
 * threshold for poison rows, and retention for published rows.
 */
@ConfigurationProperties(prefix = "taraz.outbox")
public record OutboxProperties(
        int batchSize,
        Duration sendTimeout,
        int maxAttempts,
        Duration retention,
        Duration backoffCeiling,
        String cleanupCron) {

    public OutboxProperties {
        if (batchSize <= 0) {
            batchSize = 128;
        }
        if (sendTimeout == null) {
            sendTimeout = Duration.ofSeconds(10);
        }
        if (maxAttempts <= 0) {
            maxAttempts = 10;
        }
        if (retention == null) {
            retention = Duration.ofDays(7);
        }
        if (backoffCeiling == null) {
            backoffCeiling = Duration.ofMinutes(5);
        }
        if (cleanupCron == null || cleanupCron.isBlank()) {
            cleanupCron = "0 15 3 * * *";
        }
    }
}
