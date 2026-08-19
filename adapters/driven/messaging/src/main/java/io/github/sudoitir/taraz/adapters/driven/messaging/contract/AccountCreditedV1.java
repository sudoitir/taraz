package io.github.sudoitir.taraz.adapters.driven.messaging.contract;

/** Payload for {@code account.credited} (ADR-0050). Amounts are decimal strings — ADR-0036. */
public record AccountCreditedV1(String accountId, String amount, String balanceAfter) {}
