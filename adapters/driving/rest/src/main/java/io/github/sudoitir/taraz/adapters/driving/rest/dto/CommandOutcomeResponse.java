package io.github.sudoitir.taraz.adapters.driving.rest.dto;

import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import java.util.List;

/**
 * Success body of credit/debit/transfer: the transaction id (= {@code Idempotency-Key}), whether this
 * call applied it or replayed a prior application, and the resulting balance of every touched account.
 */
public record CommandOutcomeResponse(String transactionId, OutcomeStatus status, List<BalanceEntry> balances) {}
