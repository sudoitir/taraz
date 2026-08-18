package io.github.sudoitir.taraz.adapters.driving.rest.dto;

import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /accounts/{id}/credits}. Amount in minor units (single implicit currency,
 * ADR-0036). The account id comes from the path, the transaction id from the {@code Idempotency-Key}
 * header (ADR-0043) — neither is repeated in the body.
 */
public record CreditRequest(@Positive long amount) {}
