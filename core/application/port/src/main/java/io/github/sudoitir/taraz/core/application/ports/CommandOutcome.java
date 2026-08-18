package io.github.sudoitir.taraz.core.application.ports;

import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.util.List;

/**
 * The result of applying (or replaying) a credit, debit, or transfer command: the transaction id it was
 * recorded under, whether this call applied it or replayed a prior application, and the resulting
 * balance of every account the command touched. Persisted verbatim by {@code ProcessedTransactionStore}
 * so a replay reports exactly what the original application produced.
 */
public record CommandOutcome(TransactionId transactionId, OutcomeStatus status, List<AccountBalance> balances) {}
