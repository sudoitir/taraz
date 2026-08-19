package io.github.sudoitir.taraz.core.domain.common;

/**
 * Stable catalog of predicted domain failures (ADR-0005, ADR-0011). Callers and tests assert on the code,
 * never on a message string.
 */
public enum ErrorCode {
    /** {@code amount <= 0} or fractional minor units on any operation. */
    INVALID_AMOUNT,
    /** A debit/transfer/compensation would drive a balance below zero. */
    INSUFFICIENT_FUNDS,
    /** Reconstitution/builder was handed a negative balance — corrupt state guard. */
    NEGATIVE_BALANCE,
    /** {@code transfer(A, A, …)} — ADR-0028. */
    SAME_ACCOUNT_TRANSFER,
    /** A transaction whose legs do not net to the required shape/magnitude. */
    UNBALANCED_TRANSACTION,
    /** Leg count/direction does not match the transaction type. */
    INVALID_ENTRY_SHAPE,
    /** Blank or absent client-supplied transaction id. */
    INVALID_TRANSACTION_ID,
    /** Compensating a transaction that is not {@code APPLIED} — ADR-0035. */
    COMPENSATION_TARGET_NOT_APPLIED,
    /** An operation references an account id with no corresponding account. */
    ACCOUNT_NOT_FOUND,
    /** Blank or absent client-supplied account id. */
    INVALID_ACCOUNT_ID,
    /**
     * The same {@code transactionId} was already applied with different operation parameters
     * (account, amount, or operation type) — the ADR-0041 last-guard case, detected at commit by the
     * {@code processed_transaction} unique constraint. Never silently accepted, never an unclassified
     * failure (ADR-0048).
     */
    TRANSACTION_ID_CONFLICT,
    /**
     * A row lock or a database connection could not be acquired within the configured wait budget
     * (ADR-0046 timeout ordering, ADR-0048/0054). Transient — safe to retry.
     */
    CONCURRENCY_CONFLICT
}
