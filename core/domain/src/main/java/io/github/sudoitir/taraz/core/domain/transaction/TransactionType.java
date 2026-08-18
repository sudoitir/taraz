package io.github.sudoitir.taraz.core.domain.transaction;

/**
 * Transaction kind. {@code CREDIT}/{@code DEBIT} are single-leg boundary postings (money enters/leaves the
 * service); {@code TRANSFER} is double-entry with a zero-sum invariant (ADR-0037).
 */
public enum TransactionType {
    CREDIT,
    DEBIT,
    TRANSFER
}
