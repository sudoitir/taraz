package io.github.sudoitir.taraz.core.application.ports.inbound;

import io.github.sudoitir.taraz.core.domain.common.Result;

/**
 * Opens a new account with a server-generated identifier (UUIDv7, ADR-0016) and zero balance. Takes no
 * input — server-assigned ids are the POST default, and no caller-supplied value is needed. Not a
 * financial transaction: no idempotency gate, no processed-transaction record (ADR-0021/0034 apply to
 * balance-moving operations only).
 */
public interface CreateAccountUseCase {
    Result<BalanceView> handle();
}
