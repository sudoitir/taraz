package io.github.sudoitir.taraz.core.application.ports.inbound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Immutable input to {@link DebitUseCase} (ADR-0034). No timestamp field — see {@link CreditCommand}. */
public record DebitCommand(
        @NotBlank String accountId,
        @Positive long amount,
        @NotBlank String transactionId) {}
