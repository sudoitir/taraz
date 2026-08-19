package io.github.sudoitir.taraz.adapters.driven.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.adapters.driven.persistence.common.MoneyEmbeddable;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountPersistenceMapperTest {

    private final AccountPersistenceMapper mapper = new AccountPersistenceMapper() {};

    @Test
    void toDomainRehydratesIdAndBalanceWithoutEvents() {
        AccountEntity entity = new AccountEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setCreatedAt(Instant.parse("2026-08-19T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-08-19T10:05:00Z"));
        entity.setBalance(new MoneyEmbeddable(BigDecimal.valueOf(1500)));

        Account account = mapper.toDomain(entity);

        assertThat(account.id()).isEqualTo(new AccountId(id));
        assertThat(account.balance()).isEqualTo(Money.of(1500).orElseThrow());
        assertThat(account.pullDomainEvents()).isEmpty();
    }

    @Test
    void toDomainIsScaleBlindOnBalance() {
        AccountEntity entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setCreatedAt(Instant.EPOCH);
        entity.setUpdatedAt(Instant.EPOCH);
        // A numeric column can round-trip trailing zeros (e.g. "100.00"); Money's compact
        // constructor normalizes via stripTrailingZeros so this must equal Money.of(100).
        entity.setBalance(new MoneyEmbeddable(new BigDecimal("100.00")));

        Account account = mapper.toDomain(entity);

        assertThat(account.balance()).isEqualTo(Money.of(100).orElseThrow());
    }

    @Test
    void toNewEntityCapturesIdTimestampsAndBalance() {
        Instant at = Instant.parse("2026-08-19T09:00:00Z");
        Account account =
                Account.open(new AccountId(UUID.randomUUID()), Money.ZERO, at).orElseThrow();

        AccountEntity entity = mapper.toNewEntity(account, at);

        assertThat(entity.getId()).isEqualTo(account.id().value());
        assertThat(entity.getCreatedAt()).isEqualTo(at);
        assertThat(entity.getUpdatedAt()).isEqualTo(at);
        assertThat(entity.getBalance().getMinorUnits()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
