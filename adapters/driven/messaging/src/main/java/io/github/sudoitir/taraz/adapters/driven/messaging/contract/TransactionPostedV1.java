package io.github.sudoitir.taraz.adapters.driven.messaging.contract;

/** Payload for {@code transaction.posted} (ADR-0050). {@code type} is {@code CREDIT|DEBIT|TRANSFER}. */
public record TransactionPostedV1(String transactionId, String type) {}
