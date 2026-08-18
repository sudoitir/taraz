package io.github.sudoitir.taraz.core.application.ports.inbound;

import io.github.sudoitir.taraz.core.domain.common.Result;

/**
 * CQRS read side (ADR-0007): stateless, never mutates state, opens no transaction, takes no lock. Driving
 * adapters call this directly — never through {@link BalanceService} or any write-side use case.
 */
public interface GetBalanceUseCase {
    Result<BalanceView> handle(GetBalanceQuery query);
}
