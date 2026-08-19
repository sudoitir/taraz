package io.github.sudoitir.taraz.architecturetests;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.adapters.driven.messaging.correlation.MessagingCorrelation;
import io.github.sudoitir.taraz.adapters.driving.rest.web.RestHeaders;
import org.junit.jupiter.api.Test;

/**
 * ADR-0052/0056: {@code messaging} must not depend on {@code adapters.driving.rest} (ArchUnit), so the
 * MDC key name that carries the correlation id from the HTTP filter into the outbox row is a literal
 * duplicated in both modules rather than a shared reference. This test is what turns that duplication
 * into a build-enforced invariant instead of an unguarded magic string — if either constant's value
 * drifts, this fails loudly instead of silently breaking correlation propagation.
 */
class CorrelationIdMdcKeyConstantsAgreeTest {

    @Test
    void restAndMessagingAgreeOnTheCorrelationIdMdcKey() {
        assertThat(RestHeaders.CORRELATION_ID_MDC_KEY).isEqualTo(MessagingCorrelation.CORRELATION_ID_MDC_KEY);
    }
}
