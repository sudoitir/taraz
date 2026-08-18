package io.github.sudoitir.taraz.core.domain.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void ofAcceptsZeroAndPositive() {
        assertThat(Money.of(0).orElseThrow()).isEqualTo(Money.ZERO);
        assertThat(Money.of(42).orElseThrow().minorUnits()).isEqualByComparingTo(BigDecimal.valueOf(42));
    }

    @Test
    void ofRejectsNegative() {
        assertThat(Money.of(BigDecimal.valueOf(-1)).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void operationAmountRejectsZeroAndNegative() {
        assertThat(Money.operationAmount(0).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThat(Money.operationAmount(-5).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void fractionalMinorUnitsAreRejectedAsInvalidAmount() {
        assertThat(Money.of(new BigDecimal("1.5")).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThat(Money.operationAmount(new BigDecimal("0.5")).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void fractionalConstructionBypassingFactoriesIsAProgrammerError() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.5"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balancesAccumulateExactlyBeyondLongMaxValue() {
        Money max = Money.of(Long.MAX_VALUE).orElseThrow();
        Money sum = max.plus(Money.of(Long.MAX_VALUE).orElseThrow())
                .plus(Money.of(2).orElseThrow());

        assertThat(sum.minorUnits())
                .isEqualByComparingTo(BigDecimal.valueOf(Long.MAX_VALUE)
                        .multiply(BigDecimal.TWO)
                        .add(BigDecimal.TWO));
    }

    @Test
    void plusIsExactAndNeverOverflows() {
        Money a = Money.of(new BigDecimal("999999999999999999999999999999")).orElseThrow();
        Money b = Money.of(1).orElseThrow();
        assertThat(a.plus(b).minorUnits()).isEqualByComparingTo(new BigDecimal("1000000000000000000000000000000"));
    }

    @Test
    void minusBelowZeroFailsWithInsufficientFunds() {
        Money balance = Money.of(500).orElseThrow();
        assertThat(balance.minus(Money.of(700).orElseThrow()).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
    }

    @Test
    void minusOfExactBalanceYieldsZero() {
        Money balance = Money.of(500).orElseThrow();
        assertThat(balance.minus(Money.of(500).orElseThrow()).orElseThrow()).isEqualTo(Money.ZERO);
    }

    @Test
    void equalityIsScaleBlind() {
        assertThat(new Money(new BigDecimal("1.0"))).isEqualTo(new Money(new BigDecimal("1.00")));
        assertThat(new Money(new BigDecimal("10")).hashCode()).isEqualTo(new Money(new BigDecimal("10.0")).hashCode());
    }

    @Test
    void comparisons() {
        Money five = Money.of(5).orElseThrow();
        assertThat(five.isPositive()).isTrue();
        assertThat(Money.ZERO.isPositive()).isFalse();
        assertThat(five.isGreaterThanOrEqualTo(Money.of(5).orElseThrow())).isTrue();
        assertThat(five.isGreaterThanOrEqualTo(Money.of(6).orElseThrow())).isFalse();
        assertThat(five).isGreaterThan(Money.of(4).orElseThrow());
    }

    @Test
    void signedMinusMayGoNegative() {
        assertThat(Money.ZERO.signedMinus(Money.of(3).orElseThrow()).minorUnits())
                .isEqualByComparingTo(BigDecimal.valueOf(-3));
    }
}
