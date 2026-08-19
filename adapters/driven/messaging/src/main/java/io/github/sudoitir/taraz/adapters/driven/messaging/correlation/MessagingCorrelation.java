package io.github.sudoitir.taraz.adapters.driven.messaging.correlation;

import java.util.Optional;
import org.slf4j.MDC;

/**
 * ADR-0052/0056: the messaging adapter's end of the correlation-id path. {@code messaging} must not
 * depend on {@code adapters.driving.rest} (ArchUnit), so this constant is deliberately a duplicate of
 * {@code RestHeaders.CORRELATION_ID_MDC_KEY}, not a shared reference to it — the duplication is
 * guarded by {@code architecture-tests}' {@code CorrelationIdMdcKeyConstantsAgreeTest}, which asserts
 * the two literals stay equal, turning an unguarded magic string into a build-enforced invariant.
 */
public final class MessagingCorrelation {

    public static final String CORRELATION_ID_MDC_KEY = "correlation_id";

    private MessagingCorrelation() {}

    /** The current request's correlation id, if one is bound to this thread's MDC — never fabricated. */
    public static Optional<String> currentCorrelationId() {
        String value = MDC.get(CORRELATION_ID_MDC_KEY);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }
}
