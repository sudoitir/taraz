package io.github.sudoitir.taraz.core.application.ports.inbound;

import io.github.sudoitir.taraz.core.domain.common.DomainError;
import java.io.Serial;

/**
 * The one place a predicted domain failure becomes a thrown exception: {@link BalanceService}'s
 * challenge-mandated signatures return {@code void}/{@code long} and cannot carry a {@code Result}, so
 * its facade implementation must translate a {@link io.github.sudoitir.taraz.core.domain.common.Result}
 * failure at this single boundary. No other inbound port does this (ADR-0005/0011: results, not
 * exceptions, everywhere else).
 */
public final class BalanceOperationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final DomainError error;

    public BalanceOperationException(DomainError error) {
        super(error.code() + ": " + error.message());
        this.error = error;
    }

    public DomainError error() {
        return error;
    }
}
