package io.github.sudoitir.taraz.container.it;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Meta-annotation for a Testcontainers-backed test that does NOT need a full Spring application
 * context (ADR-0053) — e.g. a schema-only test running Liquibase directly against a container's JDBC
 * connection. Same Docker-availability skip/enforce behavior as {@link TarazIntegrationTest}, without
 * paying Spring Boot context-startup cost.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(RequireDockerWhenEnforced.class)
public @interface TarazDockerTest {}
