package io.github.sudoitir.taraz.core.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.Transaction;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The challenge invariants at domain level (specs/balance-domain-model): no negative balance, no partial
 * operation, exact transfer equality, same-account rejection, compensation.
 */
class PostingServiceTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final UuidV7IdGenerator IDS = new UuidV7IdGenerator();
    private final PostingService service = new PostingService(IDS);

    private static Account accountWith(long balance) {
        return Account.reconstitute(
                        new AccountId(IDS.newId()), Money.of(balance).orElseThrow())
                .orElseThrow();
    }

    private static Money amount(long value) {
        return Money.of(value).orElseThrow();
    }

    @Test
    void creditAppliesOnce() {
        Account account = accountWith(1000);

        PostingResult result = service.credit(account, amount(500), new TransactionId("TX-1"), AT)
                .orElseThrow();

        assertThat(account.balance()).isEqualTo(amount(1500));
        assertThat(result.mutatedAccounts()).containsExactly(account);
        assertThat(result.transaction().id()).isEqualTo(new TransactionId("TX-1"));
    }

    @Test
    void transferMovesExactlyTheSameAmount() {
        Account a = accountWith(1000);
        Account b = accountWith(500);

        PostingResult result = service.transfer(a, b, amount(300), new TransactionId("TX-2"), AT)
                .orElseThrow();

        assertThat(a.balance()).isEqualTo(amount(700));
        assertThat(b.balance()).isEqualTo(amount(800));
        Transaction tx = result.transaction();
        assertThat(tx.netEffectOn(a.id())).isEqualTo(Money.ZERO.signedMinus(amount(300)));
        assertThat(tx.netEffectOn(b.id())).isEqualTo(amount(300));
        assertThat(tx.netEffectOn(a.id()).plus(tx.netEffectOn(b.id()))).isEqualTo(Money.ZERO);
        assertThat(a.domainEvents()).hasSize(1);
        assertThat(b.domainEvents()).hasSize(1);
    }

    @Test
    void failedTransferLeavesBothAccountsIdenticalToPreCallState() {
        Account a = accountWith(100); // cannot cover 300
        Account b = accountWith(500);

        var result = service.transfer(a, b, amount(300), new TransactionId("TX-3"), AT);

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(a.balance()).isEqualTo(amount(100));
        assertThat(b.balance()).isEqualTo(amount(500));
        assertThat(a.domainEvents()).isEmpty();
        assertThat(b.domainEvents()).isEmpty();
    }

    @Test
    void sameAccountTransferIsRejectedBeforeMutation() {
        Account a = accountWith(1000);

        var result = service.transfer(a, a, amount(100), new TransactionId("TX-4"), AT);

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER));
        assertThat(a.balance()).isEqualTo(amount(1000));
        assertThat(a.domainEvents()).isEmpty();
    }

    @Test
    void nonPositiveAmountsAreRejectedOnEveryOperation() {
        Account a = accountWith(1000);
        Account b = accountWith(500);

        assertThat(service.credit(a, Money.ZERO, new TransactionId("TX-5"), AT).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThat(service.debit(a, Money.ZERO, new TransactionId("TX-6"), AT).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThat(service.transfer(a, b, Money.ZERO, new TransactionId("TX-7"), AT)
                        .error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));

        assertThat(a.balance()).isEqualTo(amount(1000));
        assertThat(b.balance()).isEqualTo(amount(500));
        assertThat(a.domainEvents()).isEmpty();
        assertThat(b.domainEvents()).isEmpty();
    }

    @Test
    void compensationRestoresBalances() {
        Account a = accountWith(1000);
        Account b = accountWith(500);
        PostingResult original = service.transfer(a, b, amount(300), new TransactionId("TX-8"), AT)
                .orElseThrow();

        PostingResult compensation = service.compensate(
                        original.transaction(), List.of(a, b), new TransactionId("TX-8-CMP"), AT)
                .orElseThrow();

        assertThat(a.balance()).isEqualTo(amount(1000));
        assertThat(b.balance()).isEqualTo(amount(500));
        assertThat(compensation.transaction().compensates()).contains(new TransactionId("TX-8"));
    }

    @Test
    void compensationFailingFundsLeavesAllAccountsUnmutated() {
        Account a = accountWith(100);
        // credit 100 → balance 200, then debit 150 elsewhere → 50 left; compensating the credit needs 100
        PostingResult credited =
                service.credit(a, amount(100), new TransactionId("TX-9"), AT).orElseThrow();
        service.debit(a, amount(150), new TransactionId("TX-10"), AT).orElseThrow();
        int eventCountBefore = a.domainEvents().size();

        var result = service.compensate(credited.transaction(), List.of(a), new TransactionId("TX-9-CMP"), AT);

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(a.balance()).isEqualTo(amount(50));
        assertThat(a.domainEvents()).hasSize(eventCountBefore);
    }
}
