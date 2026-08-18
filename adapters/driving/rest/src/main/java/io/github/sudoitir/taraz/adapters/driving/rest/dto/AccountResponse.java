package io.github.sudoitir.taraz.adapters.driving.rest.dto;

import java.math.BigDecimal;

/**
 * A single account's balance on the wire. {@code balance} stays a {@link BigDecimal}: the domain's
 * {@code Money} is exact and unbounded (ADR-0036), and this DTO must not narrow it.
 */
public record AccountResponse(String accountId, BigDecimal balance) {}
