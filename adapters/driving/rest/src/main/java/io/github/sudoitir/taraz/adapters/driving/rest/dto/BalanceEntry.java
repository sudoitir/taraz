package io.github.sudoitir.taraz.adapters.driving.rest.dto;

import java.math.BigDecimal;

/** One account's resulting balance inside a {@link CommandOutcomeResponse}. */
public record BalanceEntry(String accountId, BigDecimal balance) {}
