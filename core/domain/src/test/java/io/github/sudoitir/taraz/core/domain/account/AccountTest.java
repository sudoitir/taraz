package io.github.sudoitir.taraz.core.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sudoitir.taraz.core.domain.account.event.AccountOpened;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final UuidV7IdGenerator IDS = new UuidV7IdGenerator();

    private static AccountId newAccountId() {
        return new AccountId(IDS.newId());
    }

    private static Account accountWith(long balance) {
        return Account.reconstitute(newAccountId(), Money.of(balance).orElseThrow())
                .orElseThrow();
    }

    @Test
    void openEmitsAccountOpened() {
        AccountId id = newAccountId();
        Account account = Account.open(id, Money.of(100).orElseThrow(), AT).orElseThrow();

        assertThat(account.balance()).isEqualTo(Money.of(100).orElseThrow());
        assertThat(account.domainEvents()).hasSize(1);
        assertThat(account.domainEvents().get(0)).isInstanceOfSatisfying(AccountOpened.class, e -> {
            assertThat(e.accountId()).isEqualTo(id);
            assertThat(e.occurredAt()).isEqualTo(AT);
            assertThat(e.transactionId()).isNull();
        });
    }

    @Test
    void reconstituteIsSilent() {
        Account account = accountWith(100);
        assertThat(account.domainEvents()).isEmpty();
    }

    @Test
    void openRejectsNegativeInitialBalance() {
        Money negative = Money.ZERO.signedMinus(Money.of(1).orElseThrow());
        assertThat(Account.open(newAccountId(), negative, AT).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.NEGATIVE_BALANCE));
    }

    @Test
    void debitBeyondBalanceFailsAndLeavesBalanceUnchanged() {
        Account account = accountWith(500);

        var result = account.debit(Money.of(700).orElseThrow(), new TransactionId("TX-1"), AT);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(account.balance()).isEqualTo(Money.of(500).orElseThrow());
        assertThat(account.domainEvents()).isEmpty();
    }

    @Test
    void debitOfExactBalanceSucceedsToZero() {
        Account account = accountWith(500);

        var result = account.debit(Money.of(500).orElseThrow(), new TransactionId("TX-1"), AT);

        assertThat(result.orElseThrow()).isEqualTo(Money.ZERO);
        assertThat(account.balance()).isEqualTo(Money.ZERO);
        assertThat(account.domainEvents()).hasSize(1);
        assertThat(account.domainEvents().get(0).eventType()).isEqualTo("account.debited");
        assertThat(account.domainEvents().get(0).transactionId()).isEqualTo("TX-1");
    }

    @Test
    void nonPositiveAmountsAreRejectedWithoutMutation() {
        Account account = accountWith(500);

        assertThat(account.debit(Money.ZERO, new TransactionId("TX-2"), AT).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThat(account.credit(Money.ZERO, new TransactionId("TX-3"), AT).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));

        assertThat(account.balance()).isEqualTo(Money.of(500).orElseThrow());
        assertThat(account.domainEvents()).isEmpty();
    }

    @Test
    void creditAccumulatesBeyondLongMaxValueExactly() {
        Account account = accountWith(Long.MAX_VALUE);

        account.credit(Money.of(Long.MAX_VALUE).orElseThrow(), new TransactionId("TX-4"), AT)
                .orElseThrow();

        assertThat(account.balance().minorUnits())
                .isEqualByComparingTo(BigDecimal.valueOf(Long.MAX_VALUE).multiply(BigDecimal.TWO));
    }

    @Test
    void creditEmitsEventWithResultingBalance() {
        Account account = accountWith(100);

        account.credit(Money.of(50).orElseThrow(), new TransactionId("TX-5"), AT);

        assertThat(account.domainEvents()).hasSize(1);
        assertThat(account.domainEvents().get(0).eventType()).isEqualTo("account.credited");
    }

    @Test
    void builderWithoutRequiredFieldsThrows() {
        assertThatThrownBy(() -> Account.builder().build()).isInstanceOf(IllegalStateException.class);
    }
}
