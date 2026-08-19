package io.github.sudoitir.taraz.container.config;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka broker health for {@code /actuator/health} (ADR-0060). Boot 4 ships no auto-configured Kafka
 * indicator (the health-module restructure dropped it), so the broker the outbox publisher depends on
 * would silently vanish from the health aggregate. One shared {@link AdminClient}; the probe is a
 * {@code describeCluster} with a hard 2s ceiling — a dead broker degrades to DOWN fast, never a stall.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaHealthConfiguration {

    // Name must differ from Boot's own `kafkaAdmin` (KafkaAdmin) bean in KafkaAutoConfiguration.
    @Bean(destroyMethod = "close")
    AdminClient kafkaHealthAdminClient(KafkaProperties properties) {
        return AdminClient.create(properties.buildAdminProperties());
    }

    @Bean
    HealthIndicator kafkaHealthIndicator(AdminClient kafkaHealthAdminClient) {
        return () -> {
            try {
                kafkaHealthAdminClient.describeCluster().clusterId().get(2, TimeUnit.SECONDS);
                return Health.up().build();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Health.down(e).build();
            } catch (ExecutionException | TimeoutException e) {
                return Health.down(e).build();
            }
        };
    }
}
