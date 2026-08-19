package io.github.sudoitir.taraz.container;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * Composition root and application entry point (ADR-0006).
 *
 * <p>{@code @EntityScan} is required in addition to {@code scanBasePackages}: Boot's default JPA
 * entity scan looks under this class's own package ({@code io.github.sudoitir.taraz.container}) only
 * — a sibling package like {@code adapters.driven.persistence} is invisible to it even though
 * component scanning (a separate mechanism) already covers the same tree via
 * {@code scanBasePackages}. Without this, Hibernate silently registers zero {@code @Entity} classes
 * and every repository call fails with {@code UnknownEntityTypeException} at first use — nothing in
 * context startup itself catches this.
 */
@SpringBootApplication(scanBasePackages = "io.github.sudoitir.taraz")
@EntityScan("io.github.sudoitir.taraz")
public class TarazApplication {

    public static void main(String[] args) {
        SpringApplication.run(TarazApplication.class, args);
    }
}
