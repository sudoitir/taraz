package io.github.sudoitir.taraz.core.domain.transaction;

/** Lifecycle of a transaction. Only {@code APPLIED} transactions may be compensated (ADR-0035). */
public enum TransactionStatus {
    APPLIED,
    REJECTED,
    COMPENSATED
}
