package io.github.sudoitir.taraz.core.application.ports.inbound;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.domain.common.Result;

/**
 * Atomically moves a positive amount from a source to a destination account, exactly once per
 * {@code transactionId} (ADR-0021/0026/0034/0037). Rejects a source-equals-destination request before
 * any lock or transaction (ADR-0028).
 */
public interface TransferUseCase {
    Result<CommandOutcome> handle(TransferCommand command);
}
