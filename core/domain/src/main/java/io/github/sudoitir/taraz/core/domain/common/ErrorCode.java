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
    COMPENSATION_TARGET_NOT_APPLIED
}
