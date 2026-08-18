package io.github.sudoitir.taraz.core.application.ports.inbound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Immutable input to {@link TransferUseCase} (ADR-0034). No timestamp field — see {@link CreditCommand}. */
public record TransferCommand(
        @NotBlank String sourceAccountId,
        @NotBlank String destinationAccountId,
        @Positive long amount,
        @NotBlank String transactionId) {}
