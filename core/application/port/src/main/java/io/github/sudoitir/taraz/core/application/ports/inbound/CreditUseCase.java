package io.github.sudoitir.taraz.core.application.ports.inbound;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.domain.common.Result;

/** Credits an account by a positive amount, exactly once per {@code transactionId} (ADR-0021/0034). */
public interface CreditUseCase {
    Result<CommandOutcome> handle(CreditCommand command);
}
