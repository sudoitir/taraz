package io.github.sudoitir.taraz.core.domain.transaction;

import java.util.List;

/** The input the transaction specifications evaluate: a type plus its legs, before the aggregate exists. */
public record TransactionDraft(TransactionType type, List<LedgerEntry> entries) {}
