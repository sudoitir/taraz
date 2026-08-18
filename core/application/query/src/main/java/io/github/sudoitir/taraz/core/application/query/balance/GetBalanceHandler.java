package io.github.sudoitir.taraz.core.application.query.balance;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.application.ports.outbound.AccountBalanceReadRepository;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The read side's only handler (ADR-0007): stateless, and its single dependency is the read-only
 * {@link AccountBalanceReadRepository} — there is no {@code UnitOfWork} or write-side port to inject, so
 * this class cannot open a transaction, take a lock, or mutate state even by mistake.
 */
@Service
public final class GetBalanceHandler implements GetBalanceUseCase {

    private final AccountBalanceReadRepository accounts;

    public GetBalanceHandler(AccountBalanceReadRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Override
    public Result<BalanceView> handle(GetBalanceQuery query) {
        return AccountId.of(query.accountId()).flatMap(this::findOrFail);
    }

    private Result<BalanceView> findOrFail(AccountId id) {
        return accounts.findByAccountId(id)
                .map(Result::success)
                .orElseGet(() -> Result.failure(ErrorCode.ACCOUNT_NOT_FOUND, "unknown account: " + id));
    }
}
