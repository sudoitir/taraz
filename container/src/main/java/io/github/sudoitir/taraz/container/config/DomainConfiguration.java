package io.github.sudoitir.taraz.container.config;

import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.service.PostingService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the plain, framework-free domain/application types every write handler needs (ADR-0005:
 * {@code core} has no Spring annotations of its own, so the composition root supplies these beans
 * explicitly rather than via component scanning).
 */
@Configuration(proxyBeanMethods = false)
public class DomainConfiguration {

    /** UTC: every {@code DomainEvent.occurredAt()} is an {@link java.time.Instant}; a zoned clock only invites confusion. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** ADR-0038: JUG-backed UUIDv7 generator — documented thread-safe, one shared instance. */
    @Bean
    IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }

    /** ADR-0005: the domain's stateless posting service, constructed here since {@code core.domain} has no stereotypes. */
    @Bean
    PostingService postingService(IdGenerator ids) {
        return new PostingService(ids);
    }
}
