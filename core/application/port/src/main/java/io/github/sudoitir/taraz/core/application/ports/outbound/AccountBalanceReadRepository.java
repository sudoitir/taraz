package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import java.util.Optional;

/**
 * The read side's own outbound port (ADR-0007/0033) — never shared with the write side's
 * {@link AccountRepository}, so a read never opens a transaction or takes a lock.
 */
public interface AccountBalanceReadRepository {
    Optional<BalanceView> findByAccountId(AccountId id);
}
