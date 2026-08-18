package io.github.sudoitir.taraz.adapters.driving.rest.web;

/** Custom header names of the REST contract (ADR-0043). Single source — never spell them inline. */
public final class RestHeaders {

    /** Client-supplied key that IS the command's {@code transactionId}. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Set to {@code "true"} on a response when the command was replayed, not re-applied. */
    public static final String IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";

    /** Zalando correlation id: echoed when supplied, generated otherwise, bound to MDC. */
    public static final String X_FLOW_ID = "X-Flow-ID";

    private RestHeaders() {}
}
