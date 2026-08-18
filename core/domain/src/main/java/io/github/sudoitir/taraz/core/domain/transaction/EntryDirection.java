package io.github.sudoitir.taraz.core.domain.transaction;

/** Direction of a ledger leg. {@link #reversed()} is the basis of compensation (ADR-0035). */
public enum EntryDirection {
    DEBIT,
    CREDIT;

    public EntryDirection reversed() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
