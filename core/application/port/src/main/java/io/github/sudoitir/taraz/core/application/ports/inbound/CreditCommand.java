package io.github.sudoitir.taraz.core.application.ports.inbound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Immutable input to {@link CreditUseCase} (ADR-0034). No timestamp field — {@code occurredAt} is
 * resolved by the handler from an injected {@code Clock}, never supplied by the caller (ADR-0005: no
 * ambient state, but also no client-forgeable ledger time).
 */
public record CreditCommand(
        @NotBlank String accountId,
        @Positive long amount,
        @NotBlank String transactionId) {}
