package io.github.sudoitir.taraz.adapters.driven.persistence.account;

import io.github.sudoitir.taraz.adapters.driven.persistence.common.MoneyEmbeddable;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.time.Instant;
import org.mapstruct.Mapper;

/**
 * Entity ↔ domain mapping for {@link Account} (ADR-0015/0031). Hand-written as {@code default} methods
 * rather than {@code @Mapping}-annotated ones: {@code Account} has no public constructor and no
 * setters (it is built only through {@link Account#reconstitute}, which returns a validating
 * {@code Result}), so MapStruct's usual generate-a-setter-chain approach cannot apply here — this is
 * the seam ADR-0031 expects for aggregates with private construction.
 */
@Mapper(componentModel = "spring")
public interface AccountPersistenceMapper {

    /**
     * Rehydrates the domain aggregate from persisted state. Deliberately uses
     * {@link Account#reconstitute}, not {@link Account#open}: reconstitution emits no domain events —
     * replaying {@code AccountOpened} on every load would flood the outbox (ADR-0009/0010). The
     * {@code ck_account_balance_non_negative} CHECK constraint makes a negative persisted balance
     * unreachable, so {@code orElseThrow} here is a programmer assertion, not a business outcome.
     */
    default Account toDomain(AccountEntity entity) {
        AccountId id = new AccountId(entity.getId());
        Money balance = new Money(entity.getBalance().getMinorUnits());
        return Account.reconstitute(id, balance).orElseThrow();
    }

    /**
     * Builds a brand-new row for an account that was never locked because it does not yet exist — the
     * account-opening use case's path (ADR-0034 delta: {@code AccountRepository.saveAll} handles both
     * insert and update).
     */
    default AccountEntity toNewEntity(Account account, Instant now) {
        AccountEntity entity = new AccountEntity();
        entity.setId(account.id().value());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setBalance(new MoneyEmbeddable(account.balance().minorUnits()));
        return entity;
    }
}
