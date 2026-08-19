package io.github.sudoitir.taraz.container.it;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared, JVM-static containers for every Testcontainers-backed IT (ADR-0022/0053) — one instance per
 * test run, not per test class, so a full suite does not pay startup cost per class. Image versions
 * are pinned to match {@code compose.yaml} exactly (kept in sync by hand; {@link TestImages} is the
 * single place both this class and any future one reads from).
 */
@Testcontainers
public abstract class AbstractTarazIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(TestImages.POSTGRES)
            .withDatabaseName("taraz")
            .withUsername("taraz")
            .withPassword("taraz");

    /**
     * No {@code --requirepass}: {@code @ServiceConnection} supplies host/port/password (empty here)
     * from the container directly, overriding {@code spring.data.redis.*} entirely — the production
     * default password in {@code application.yaml} is irrelevant to these tests.
     */
    @ServiceConnection
    static final RedisContainer VALKEY =
            new RedisContainer(DockerImageName.parse(TestImages.VALKEY).asCompatibleSubstituteFor("redis"));

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer(TestImages.KAFKA);

    static {
        POSTGRES.start();
        VALKEY.start();
        KAFKA.start();
    }
}
