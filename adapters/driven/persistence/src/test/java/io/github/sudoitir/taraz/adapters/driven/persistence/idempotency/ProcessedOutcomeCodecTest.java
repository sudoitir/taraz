package io.github.sudoitir.taraz.adapters.driven.persistence.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sudoitir.taraz.core.application.ports.AccountBalance;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessedOutcomeCodecTest {

    private final ProcessedOutcomeCodec codec = new ProcessedOutcomeCodec();

    @Test
    void roundTripsBalancesAsDecimalStrings() {
        TransactionId id = new TransactionId("TX-1");
        AccountId account = new AccountId(UUID.randomUUID());
        CommandOutcome outcome = new CommandOutcome(
                id,
                OutcomeStatus.APPLIED,
                List.of(new AccountBalance(account, Money.of(1500).orElseThrow())));

        String json = codec.toJson(outcome);

        // ADR-0036: amounts must never appear as JSON numbers — a JSON number can round-trip
        // through a double and silently lose precision on an exact BigDecimal value.
        assertThat(json).doesNotContain(":1500,").doesNotContain(":1500}");
        assertThat(json).contains("\"1500\"");

        CommandOutcome restored = codec.fromJson(id, json);
        assertThat(restored.transactionId()).isEqualTo(id);
        assertThat(restored.status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(restored.balances()).hasSize(1);
        assertThat(restored.balances().get(0).accountId()).isEqualTo(account);
        assertThat(restored.balances().get(0).balance())
                .isEqualTo(Money.of(1500).orElseThrow());
    }

    @Test
    void roundTripsMultipleBalancesForATransfer() {
        TransactionId id = new TransactionId("TX-2");
        AccountId source = new AccountId(UUID.randomUUID());
        AccountId destination = new AccountId(UUID.randomUUID());
        CommandOutcome outcome = new CommandOutcome(
                id,
                OutcomeStatus.APPLIED,
                List.of(
                        new AccountBalance(source, Money.of(700).orElseThrow()),
                        new AccountBalance(destination, Money.of(800).orElseThrow())));

        CommandOutcome restored = codec.fromJson(id, codec.toJson(outcome));

        assertThat(restored.balances()).hasSize(2);
        assertThat(restored.balances())
                .extracting(AccountBalance::balance)
                .containsExactly(Money.of(700).orElseThrow(), Money.of(800).orElseThrow());
    }

    @Test
    void rejectsAnUnsupportedSchemaVersion() {
        String futureVersionJson = "{\"v\":99,\"balances\":[]}";

        assertThatThrownBy(() -> codec.fromJson(new TransactionId("TX-3"), futureVersionJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("99");
    }

    @Test
    void preservesExactBigDecimalPrecisionAcrossAWideRange() {
        TransactionId id = new TransactionId("TX-4");
        AccountId account = new AccountId(UUID.randomUUID());
        Money large = new Money(new BigDecimal("123456789012345678901234567890"));
        CommandOutcome outcome =
                new CommandOutcome(id, OutcomeStatus.APPLIED, List.of(new AccountBalance(account, large)));

        CommandOutcome restored = codec.fromJson(id, codec.toJson(outcome));

        assertThat(restored.balances().get(0).balance()).isEqualTo(large);
    }
}
