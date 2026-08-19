package io.github.sudoitir.taraz.adapters.driven.messaging.outbox;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@link OutboxPollingPublisher}'s and {@link OutboxCleanupJob}'s {@code @Scheduled} tasks,
 * binds {@link OutboxProperties}, and coordinates both across horizontally-scaled pods with ShedLock
 * (ADR-0057) — without it, two pods each running {@code FOR UPDATE SKIP LOCKED} independently is safe
 * for the outbox rows themselves (no double-send within one claim) but wastes a full poll's connection
 * and query on every pod beyond the first, and the cleanup job's chunked DELETE would run redundantly
 * on every pod. Lives in {@code messaging}, not {@code container}: the module owns its own lifecycle
 * (ADR-0049).
 *
 * <p>{@code defaultLockAtMostFor} is the ceiling on how long a lock survives a pod that died mid-task
 * without releasing it — long enough that a legitimately slow batch is never preempted mid-flight,
 * short enough that a genuinely dead pod's lock is reclaimed quickly. Each task overrides it with its
 * own more precise value via {@code @SchedulerLock}.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "5m")
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxPublisherConfiguration {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("shedlock")
                // ADR-0057: database time, not each pod's system clock — avoids stale/premature lock
                // expiry from clock drift between pods.
                .usingDbTime()
                .build());
    }
}
