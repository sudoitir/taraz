package io.github.sudoitir.taraz.core.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpecificationTest {

    private static final class IsPositive extends AbstractSpecification<Integer> {
        @Override
        public boolean isSatisfiedBy(Integer candidate) {
            return candidate > 0;
        }

        @Override
        public DomainError violation(Integer candidate) {
            return new DomainError(ErrorCode.INVALID_AMOUNT, "must be positive");
        }
    }

    private static final class IsSmall extends AbstractSpecification<Integer> {
        @Override
        public boolean isSatisfiedBy(Integer candidate) {
            return candidate < 10;
        }

        @Override
        public DomainError violation(Integer candidate) {
            return new DomainError(ErrorCode.INSUFFICIENT_FUNDS, "must be small");
        }
    }

    private static final class IsEven extends AbstractSpecification<Integer> {
        @Override
        public boolean isSatisfiedBy(Integer candidate) {
            return candidate % 2 == 0;
        }

        @Override
        public DomainError violation(Integer candidate) {
            return new DomainError(ErrorCode.UNBALANCED_TRANSACTION, "must be even");
        }
    }

    private final Specification<Integer> positive = new IsPositive();
    private final Specification<Integer> small = new IsSmall();
    private final Specification<Integer> even = new IsEven();

    @Test
    void checkReturnsSuccessWithCandidateWhenSatisfied() {
        Result<Integer> result = positive.check(5);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow()).isEqualTo(5);
    }

    @Test
    void checkReturnsViolationWhenNotSatisfied() {
        Result<Integer> result = positive.check(-1);
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void andIsFailFastAndLeftBiased() {
        Specification<Integer> both = positive.and(small);

        assertThat(both.isSatisfiedBy(5)).isTrue();
        // both fail on -1: the FIRST violation (positive) is reported
        assertThat(both.check(-1).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        // only right fails on 20: the right violation is reported
        assertThat(both.check(20).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
    }

    @Test
    void orIsSatisfiedWhenEitherSideHolds() {
        Specification<Integer> either = small.or(even);

        assertThat(either.isSatisfiedBy(5)).isTrue(); // small only
        assertThat(either.isSatisfiedBy(20)).isTrue(); // even only
        assertThat(either.isSatisfiedBy(4)).isTrue(); // both
        assertThat(either.isSatisfiedBy(11)).isFalse(); // neither
        assertThat(either.check(11).error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
    }

    @Test
    void notReportsTheExplicitErrorWhenInnerHolds() {
        DomainError error = new DomainError(ErrorCode.SAME_ACCOUNT_TRANSFER, "must not be positive");
        Specification<Integer> notPositive = positive.not(error);

        assertThat(notPositive.isSatisfiedBy(-1)).isTrue();
        assertThat(notPositive.isSatisfiedBy(5)).isFalse();
        assertThat(notPositive.check(5).error()).hasValue(error);
    }
}
