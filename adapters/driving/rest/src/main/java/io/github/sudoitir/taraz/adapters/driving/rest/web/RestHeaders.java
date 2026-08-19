package io.github.sudoitir.taraz.adapters.driving.rest.web;

/** Custom header names of the REST contract (ADR-0043). Single source — never spell them inline. */
public final class RestHeaders {

    /** Client-supplied key that IS the command's {@code transactionId}. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Set to {@code "true"} on a response when the command was replayed, not re-applied. */
    public static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    /**
     * Correlation id: echoed when supplied, generated otherwise, bound to MDC (ADR-0056, which
     * renamed this header from ADR-0043's original {@code X-Flow-ID} to the more widely recognized
     * {@code X-Correlation-ID}).
     */
    public static final String X_CORRELATION_ID = "X-Correlation-ID";

    /** MDC key {@link io.github.sudoitir.taraz.adapters.driving.rest.web.CorrelationIdFilter} binds {@link #X_CORRELATION_ID}'s value under. */
    public static final String CORRELATION_ID_MDC_KEY = "correlation_id";

    private RestHeaders() {}
}
