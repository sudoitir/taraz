package io.github.sudoitir.taraz.adapters.driving.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Body of {@code POST /transfers}. The transaction id comes from the {@code Idempotency-Key} header. */
public record TransferRequest(
        @NotBlank String sourceAccountId,
        @NotBlank String destinationAccountId,
        @Positive long amount) {}
