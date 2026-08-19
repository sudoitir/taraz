package io.github.sudoitir.taraz.adapters.driven.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.LedgerEntry;
import io.github.sudoitir.taraz.core.domain.transaction.Transaction;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionPersistenceMapperTest {

    private static final Instant AT = Instant.parse("2026-08-19T10:00:00Z");
    private static final UuidV7IdGenerator IDS = new UuidV7IdGenerator();

    private final TransactionPersistenceMapper mapper = new TransactionPersistenceMapper() {};

    @Test
    void toEntityCapturesSurrogateIdAndExternalIdSeparately() {
        AccountId source = new AccountId(IDS.newId());
        AccountId destination = new AccountId(IDS.newId());
        Transaction transfer = Transaction.transfer(
                        new TransactionId("TX-1"),
                        source,
                        destination,
                        Money.of(300).orElseThrow(),
                        AT,
                        IDS)
                .orElseThrow();
        UUID surrogateId = IDS.newId();

        LedgerTransactionEntity entity = mapper.toEntity(transfer, surrogateId, AT);

        assertThat(entity.getId()).isEqualTo(surrogateId);
        assertThat(entity.getExternalId()).isEqualTo("TX-1");
        assertThat(entity.getType()).isEqualTo("TRANSFER");
        assertThat(entity.getStatus()).isEqualTo("APPLIED");
        assertThat(entity.getOccurredAt()).isEqualTo(AT);
        assertThat(entity.getCompensatesExternalId()).isNull();
    }

    @Test
    void toEntityCapturesCompensationLink() {
        AccountId account = new AccountId(IDS.newId());
        Transaction original = Transaction.credit(
                        new TransactionId("TX-1"), account, Money.of(500).orElseThrow(), AT, IDS)
                .orElseThrow();
        Transaction compensation = Transaction.compensationOf(original, new TransactionId("TX-1-REV"), AT, IDS)
                .orElseThrow();

        LedgerTransactionEntity entity = mapper.toEntity(compensation, IDS.newId(), AT);

        assertThat(entity.getExternalId()).isEqualTo("TX-1-REV");
        assertThat(entity.getCompensatesExternalId()).isEqualTo("TX-1");
        assertThat(entity.getType()).isEqualTo("DEBIT");
    }

    @Test
    void entryEntityCarriesDirectionAndAmount() {
        AccountId account = new AccountId(IDS.newId());
        Transaction debit = Transaction.debit(
                        new TransactionId("TX-2"), account, Money.of(700).orElseThrow(), AT, IDS)
                .orElseThrow();
        LedgerEntry leg = debit.entries().get(0);
        UUID parentId = IDS.newId();

        LedgerEntryEntity entity = mapper.toEntity(leg, parentId, AT);

        assertThat(entity.getId()).isEqualTo(leg.id().value());
        assertThat(entity.getTransactionId()).isEqualTo(parentId);
        assertThat(entity.getAccountId()).isEqualTo(account.value());
        assertThat(entity.getDirection()).isEqualTo("DEBIT");
        assertThat(entity.getAmount().getMinorUnits()).isEqualByComparingTo(BigDecimal.valueOf(700));
    }
}
