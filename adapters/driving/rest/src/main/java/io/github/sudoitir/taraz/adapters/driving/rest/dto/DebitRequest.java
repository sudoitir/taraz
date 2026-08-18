package io.github.sudoitir.taraz.adapters.driving.rest.dto;

import jakarta.validation.constraints.Positive;

/** Body of {@code POST /accounts/{id}/debits}. See {@link CreditRequest} for field sourcing. */
public record DebitRequest(@Positive long amount) {}
