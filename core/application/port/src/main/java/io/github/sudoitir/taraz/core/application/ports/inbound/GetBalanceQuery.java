package io.github.sudoitir.taraz.core.application.ports.inbound;

import jakarta.validation.constraints.NotBlank;

/** Immutable input to {@link GetBalanceUseCase} — the read side never accepts a write-side command. */
public record GetBalanceQuery(@NotBlank String accountId) {}
