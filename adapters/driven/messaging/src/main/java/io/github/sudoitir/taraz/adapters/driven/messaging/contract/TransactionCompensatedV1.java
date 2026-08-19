package io.github.sudoitir.taraz.adapters.driven.messaging.contract;

/** Payload for {@code transaction.compensated} (ADR-0050/0035). */
public record TransactionCompensatedV1(String transactionId, String compensates) {}
