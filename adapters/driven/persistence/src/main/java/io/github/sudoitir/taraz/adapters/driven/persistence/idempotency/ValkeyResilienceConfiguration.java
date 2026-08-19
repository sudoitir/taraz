package io.github.sudoitir.taraz.adapters.driven.persistence.idempotency;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.TimeoutOptions;
import java.time.Duration;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-0021/0041/0054: without this, a dead Valkey turns every request into a stall rather than a fast
 * degrade to {@link io.github.sudoitir.taraz.core.application.ports.outbound.GateDecision.Unknown}.
 * Lettuce's default {@code DisconnectedBehavior.DEFAULT} queues commands while a reconnect is in
 * progress, so a plain {@code try/catch} in {@link ValkeyIdempotencyGate} is not enough on its own —
 * the client itself must be configured to reject immediately instead of queueing.
 */
@Configuration(proxyBeanMethods = false)
class ValkeyResilienceConfiguration {

    @Bean
    LettuceClientOptionsBuilderCustomizer failFastLettuceClientOptions() {
        return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .autoReconnect(true)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200)));
    }
}
