package io.github.sudoitir.taraz.core.domain.money;

import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Money in minor units, single implicit currency (ADR-0036). Backed by {@link BigDecimal}: arithmetic is
 * exact and unbounded — there is no overflow failure mode; the only failing arithmetic is a subtraction
 * that would go negative ({@link ErrorCode#INSUFFICIENT_FUNDS}).
 *
 * <p>Values are always whole minor units (the API boundary supplies {@code long}); the compact
 * constructor normalizes via {@link BigDecimal#stripTrailingZeros()} so equality is scale-blind
 * ({@code 1.0} equals {@code 1.00}).
 *
 * <p>The record itself may hold a negative value (e.g. the signed result of
 * {@code Transaction.netEffectOn}); the constraints live in the factories: {@link #of} for balances
 * ({@code >= 0}) and {@link #operationAmount} for operation amounts ({@code > 0}).
 */
public record Money(BigDecimal minorUnits) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    /** Normalizes so equality is scale-blind. Fractional minor units are a programmer error here — the validating factories ({@link #of}, {@link #operationAmount}) report them as {@link ErrorCode#INVALID_AMOUNT}. */
    public Money {
        Objects.requireNonNull(minorUnits, "minorUnits");
        minorUnits = minorUnits.signum() == 0 ? BigDecimal.ZERO : minorUnits.stripTrailingZeros();
        if (minorUnits.scale() > 0) {
            throw new IllegalArgumentException("minorUnits must be a whole number: " + minorUnits);
        }
    }

    /** A balance or any non-negative amount. */
    public static Result<Money> of(BigDecimal minorUnits) {
        return validateWhole(minorUnits)
                .flatMap(v -> v.signum() < 0
                        ? Result.failure(ErrorCode.INVALID_AMOUNT, "amount must be >= 0: " + v)
                        : Result.success(new Money(v)));
    }

    public static Result<Money> of(long minorUnits) {
        return Result.success(new Money(BigDecimal.valueOf(minorUnits)));
    }

    /** An operation amount: strictly positive. */
    public static Result<Money> operationAmount(BigDecimal minorUnits) {
        return validateWhole(minorUnits)
                .flatMap(v -> v.signum() <= 0
                        ? Result.failure(ErrorCode.INVALID_AMOUNT, "operation amount must be > 0: " + v)
                        : Result.success(new Money(v)));
    }

    public static Result<Money> operationAmount(long minorUnits) {
        return operationAmount(BigDecimal.valueOf(minorUnits));
    }

    /** Exact and unbounded — cannot overflow. */
    public Money plus(Money other) {
        return new Money(minorUnits.add(other.minorUnits));
    }

    /** Fails with {@link ErrorCode#INSUFFICIENT_FUNDS} when the result would be negative. */
    public Result<Money> minus(Money other) {
        BigDecimal result = minorUnits.subtract(other.minorUnits);
        return result.signum() < 0
                ? Result.failure(
                        ErrorCode.INSUFFICIENT_FUNDS, "balance " + minorUnits + " cannot cover " + other.minorUnits)
                : Result.success(new Money(result));
    }

    /** Signed difference — may be negative (used for net effects, never for balances). */
    public Money signedMinus(Money other) {
        return new Money(minorUnits.subtract(other.minorUnits));
    }

    public boolean isPositive() {
        return minorUnits.signum() > 0;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        return minorUnits.compareTo(other.minorUnits) >= 0;
    }

    @Override
    public int compareTo(Money other) {
        return minorUnits.compareTo(other.minorUnits);
    }

    @Override
    public String toString() {
        return minorUnits.toPlainString();
    }

    private static Result<BigDecimal> validateWhole(BigDecimal value) {
        Objects.requireNonNull(value, "minorUnits");
        BigDecimal stripped = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return stripped.scale() > 0
                ? Result.failure(ErrorCode.INVALID_AMOUNT, "minor units must be a whole number: " + value)
                : Result.success(stripped);
    }
}
