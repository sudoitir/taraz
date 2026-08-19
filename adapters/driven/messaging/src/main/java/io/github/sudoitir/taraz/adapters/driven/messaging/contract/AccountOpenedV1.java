package io.github.sudoitir.taraz.adapters.driven.messaging.contract;

/** Payload for {@code account.opened} (ADR-0050). Amounts are decimal strings — ADR-0036. */
public record AccountOpenedV1(String accountId, String balance) {}
