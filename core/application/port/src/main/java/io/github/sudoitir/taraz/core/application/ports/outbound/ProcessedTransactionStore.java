package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.util.Optional;

/**
 * The authoritative idempotency record (ADR-0021/0041): a durable, unique-constrained mapping from
 * {@link TransactionId} to the {@link CommandOutcome} it produced. {@link #find} is called only after
 * the relevant account rows are locked (ADR-0026), inside the {@link UnitOfWork} boundary — that
 * ordering is what makes concurrent duplicates serialize instead of racing.
 */
public interface ProcessedTransactionStore {
    Optional<CommandOutcome> find(TransactionId id);

    void record(TransactionId id, CommandOutcome outcome);
}
