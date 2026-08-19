package io.github.sudoitir.taraz.container.it;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Meta-annotation for every Testcontainers-backed integration test (ADR-0022/0053). Auto-skips
 * without Docker locally; {@link RequireDockerWhenEnforced} turns that same skip into a failure when
 * {@code -Dtaraz.require.docker=true} (the {@code ci} Maven profile). Deliberately stays in surefire
 * ({@code *IT} naming is a convention here, not a failsafe include pattern — ADR-0053 keeps everything
 * inside {@code ./mvnw test}).
 *
 * <p>{@code @ActiveProfiles("test")} layers {@code application-test.yaml} on top of the main
 * {@code application.yaml} — profile-specific files augment the base config, they never replace it.
 * A same-named {@code application.yaml} directly under {@code src/test/resources} would instead
 * <em>shadow</em> the main one entirely on the test classpath (Spring Boot loads only the first
 * {@code application.yaml} match it finds, it does not merge two files with the same name from
 * different classpath roots) — found the hard way: an earlier version of this test setup silently
 * dropped every datasource/liquibase/redis/kafka setting from every integration test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(RequireDockerWhenEnforced.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public @interface TarazIntegrationTest {}
