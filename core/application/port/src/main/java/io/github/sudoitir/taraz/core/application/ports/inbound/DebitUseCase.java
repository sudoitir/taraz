package io.github.sudoitir.taraz.core.application.ports.inbound;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.domain.common.Result;

/**
 * Debits an account by a positive amount, only when funds are sufficient, exactly once per
 * {@code transactionId} (ADR-0021/0034).
 */
public interface DebitUseCase {
    Result<CommandOutcome> handle(DebitCommand command);
}
