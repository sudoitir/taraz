package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.domain.transaction.Transaction;

/** Persists a {@link Transaction} (with its ledger entries) within the {@link UnitOfWork} boundary. */
public interface TransactionRepository {
    void save(Transaction transaction);
}
