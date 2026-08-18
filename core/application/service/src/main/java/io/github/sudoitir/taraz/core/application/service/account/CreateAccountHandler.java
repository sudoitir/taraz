package io.github.sudoitir.taraz.core.application.service.account;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.outbound.AccountRepository;
import io.github.sudoitir.taraz.core.application.ports.outbound.OutboxAppender;
import io.github.sudoitir.taraz.core.application.ports.outbound.UnitOfWork;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The open-account use case. The atomic unit — open, persist, publish the event — is exactly the body
 * of the single {@link UnitOfWork#inTransaction} call (ADR-0018/0040), same boundary discipline as the
 * financial handlers. Deliberately no {@code IdempotencyGate} and no {@code ProcessedTransactionStore}:
 * opening carries no client transaction id, so there is nothing to deduplicate (each call legitimately
 * creates a new account).
 */
@Service
public final class CreateAccountHandler implements CreateAccountUseCase {

    private final IdGenerator ids;
    private final Clock clock;
    private final UnitOfWork unitOfWork;
    private final AccountRepository accounts;
    private final OutboxAppender outbox;

    public CreateAccountHandler(
            IdGenerator ids, Clock clock, UnitOfWork unitOfWork, AccountRepository accounts, OutboxAppender outbox) {
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
    }

    @Override
    public Result<BalanceView> handle() {
        AccountId id = new AccountId(ids.newId());
        return unitOfWork.inTransaction(() -> Account.open(id, Money.ZERO, clock.instant())
                .map(account -> {
                    accounts.saveAll(List.of(account));
                    outbox.append(account.pullDomainEvents());
                    return new BalanceView(account.id(), account.balance());
                }));
    }
}
