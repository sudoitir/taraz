package io.github.sudoitir.taraz.core.domain.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.event.TransactionCompensated;
import io.github.sudoitir.taraz.core.domain.transaction.event.TransactionPosted;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final UuidV7IdGenerator IDS = new UuidV7IdGenerator();

    private static AccountId account() {
        return new AccountId(IDS.newId());
    }

    private static TransactionId txId(String value) {
        return new TransactionId(value);
    }

    private static Money amount(long value) {
        return Money.of(value).orElseThrow();
    }

    @Test
    void transferHasTwoOppositeLegsNettingToZero() {
        AccountId a = account();
        AccountId b = account();

        Transaction tx =
                Transaction.transfer(txId("TX-1"), a, b, amount(300), AT, IDS).orElseThrow();

        assertThat(tx.entries()).hasSize(2);
        assertThat(tx.entries().stream().map(LedgerEntry::direction))
                .containsExactlyInAnyOrder(EntryDirection.DEBIT, EntryDirection.CREDIT);
        assertThat(tx.netEffectOn(a)).isEqualTo(Money.ZERO.signedMinus(amount(300)));
        assertThat(tx.netEffectOn(b)).isEqualTo(amount(300));
        assertThat(tx.netEffectOn(a).plus(tx.netEffectOn(b))).isEqualTo(Money.ZERO);
        assertThat(tx.domainEvents()).hasSize(1);
        assertThat(tx.domainEvents().get(0)).isInstanceOf(TransactionPosted.class);
        assertThat(tx.domainEvents().get(0).transactionId()).isEqualTo("TX-1");
    }

    @Test
    void creditAndDebitHaveOneBoundaryLeg() {
        AccountId a = account();

        Transaction credit =
                Transaction.credit(txId("TX-2"), a, amount(100), AT, IDS).orElseThrow();
        Transaction debit =
                Transaction.debit(txId("TX-3"), a, amount(100), AT, IDS).orElseThrow();

        assertThat(credit.entries()).extracting(LedgerEntry::direction).containsExactly(EntryDirection.CREDIT);
        assertThat(debit.entries()).extracting(LedgerEntry::direction).containsExactly(EntryDirection.DEBIT);
        assertThat(credit.netEffectOn(a)).isEqualTo(amount(100));
        assertThat(debit.netEffectOn(a)).isEqualTo(Money.ZERO.signedMinus(amount(100)));
    }

    @Test
    void netEffectOnNonPartyAccountIsZero() {
        Transaction tx = Transaction.transfer(txId("TX-4"), account(), account(), amount(10), AT, IDS)
                .orElseThrow();
        assertThat(tx.netEffectOn(account())).isEqualTo(Money.ZERO);
    }

    @Test
    void sameAccountTransferIsRejected() {
        AccountId a = account();

        var result = Transaction.transfer(txId("TX-5"), a, a, amount(100), AT, IDS);

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER));
    }

    @Test
    void legShapeMismatchIsRejected() {
        AccountId a = account();
        LedgerEntry creditLeg = LedgerEntry.builder()
                .id(new EntryId(IDS.newId()))
                .accountId(a)
                .direction(EntryDirection.CREDIT)
                .amount(amount(100))
                .build();

        // CREDIT type with two legs
        var twoLegs = Transaction.builder()
                .id(txId("TX-6"))
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.APPLIED)
                .occurredAt(AT)
                .entries(List.of(creditLeg, creditLeg))
                .build();
        assertThat(twoLegs.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ENTRY_SHAPE));

        // DEBIT type with a credit leg
        var wrongDirection = Transaction.builder()
                .id(txId("TX-7"))
                .type(TransactionType.DEBIT)
                .status(TransactionStatus.APPLIED)
                .occurredAt(AT)
                .entries(List.of(creditLeg))
                .build();
        assertThat(wrongDirection.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_ENTRY_SHAPE));
    }

    @Test
    void nonUniformLegAmountsAreUnbalanced() {
        AccountId a = account();
        AccountId b = account();
        List<LedgerEntry> legs = List.of(
                LedgerEntry.builder()
                        .id(new EntryId(IDS.newId()))
                        .accountId(a)
                        .direction(EntryDirection.DEBIT)
                        .amount(amount(100))
                        .build(),
                LedgerEntry.builder()
                        .id(new EntryId(IDS.newId()))
                        .accountId(b)
                        .direction(EntryDirection.CREDIT)
                        .amount(amount(50))
                        .build());

        var result = Transaction.builder()
                .id(txId("TX-8"))
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.APPLIED)
                .occurredAt(AT)
                .entries(legs)
                .build();

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.UNBALANCED_TRANSACTION));
    }

    @Test
    void nonPositiveLegAmountIsRejected() {
        AccountId a = account();
        LedgerEntry zeroLeg = LedgerEntry.builder()
                .id(new EntryId(IDS.newId()))
                .accountId(a)
                .direction(EntryDirection.CREDIT)
                .amount(Money.ZERO)
                .build();

        var result = Transaction.builder()
                .id(txId("TX-9"))
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.APPLIED)
                .occurredAt(AT)
                .entries(List.of(zeroLeg))
                .build();

        assertThat(result.error()).hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void compensationReversesEveryLegAndLinksOriginal() {
        AccountId a = account();
        AccountId b = account();
        Transaction original =
                Transaction.transfer(txId("TX-10"), a, b, amount(300), AT, IDS).orElseThrow();

        Transaction compensation =
                Transaction.compensationOf(original, txId("TX-10-CMP"), AT, IDS).orElseThrow();

        assertThat(compensation.compensates()).contains(txId("TX-10"));
        assertThat(compensation.entries()).hasSize(2);
        // every leg reversed, same accounts and amounts
        assertThat(compensation.netEffectOn(a)).isEqualTo(amount(300));
        assertThat(compensation.netEffectOn(b)).isEqualTo(Money.ZERO.signedMinus(amount(300)));
        // original untouched: status, entries, no new events
        assertThat(original.status()).isEqualTo(TransactionStatus.APPLIED);
        assertThat(original.domainEvents()).hasSize(1);
        assertThat(compensation.domainEvents()).hasSize(1);
        assertThat(compensation.domainEvents().get(0)).isInstanceOf(TransactionCompensated.class);
    }

    @Test
    void compensationOfCreditAndDebitAlsoReverse() {
        AccountId a = account();
        Transaction credit =
                Transaction.credit(txId("TX-11"), a, amount(100), AT, IDS).orElseThrow();
        Transaction debit =
                Transaction.debit(txId("TX-12"), a, amount(100), AT, IDS).orElseThrow();

        Transaction cmpCredit =
                Transaction.compensationOf(credit, txId("TX-11-C"), AT, IDS).orElseThrow();
        Transaction cmpDebit =
                Transaction.compensationOf(debit, txId("TX-12-C"), AT, IDS).orElseThrow();

        assertThat(cmpCredit.entries().get(0).direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(cmpDebit.entries().get(0).direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(cmpCredit.netEffectOn(a).plus(credit.netEffectOn(a))).isEqualTo(Money.ZERO);
        assertThat(cmpDebit.netEffectOn(a).plus(debit.netEffectOn(a))).isEqualTo(Money.ZERO);
    }

    @Test
    void compensationOfNonAppliedTransactionIsRejected() {
        AccountId a = account();
        Transaction rejected = Transaction.builder()
                .id(txId("TX-13"))
                .type(TransactionType.CREDIT)
                .status(TransactionStatus.REJECTED)
                .occurredAt(AT)
                .entries(List.of(LedgerEntry.builder()
                        .id(new EntryId(IDS.newId()))
                        .accountId(a)
                        .direction(EntryDirection.CREDIT)
                        .amount(amount(100))
                        .build()))
                .build()
                .orElseThrow();

        var result = Transaction.compensationOf(rejected, txId("TX-13-C"), AT, IDS);

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.COMPENSATION_TARGET_NOT_APPLIED));
    }

    @Test
    void transactionIdBlankIsRejectedViaValidatingFactory() {
        assertThat(TransactionId.of("  ").error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TRANSACTION_ID));
        assertThat(TransactionId.of(null).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TRANSACTION_ID));
    }

    @Test
    void entriesAreUnmodifiable() {
        Transaction tx =
                Transaction.credit(txId("TX-14"), account(), amount(1), AT, IDS).orElseThrow();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.entries().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
