package io.github.sudoitir.taraz.core.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResultTest {

    @Test
    void successMapsValue() {
        Result<Integer> result = Result.<Integer>success(2).map(i -> i * 3);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow()).isEqualTo(6);
        assertThat(result.error()).isEmpty();
    }

    @Test
    void successFlatMapsToNextResult() {
        Result<Integer> result = Result.<Integer>success(2).flatMap(i -> Result.success(i + 1));
        assertThat(result.orElseThrow()).isEqualTo(3);
    }

    @Test
    void failureShortCircuitsMapAndFlatMap() {
        Result<Integer> failure = Result.failure(ErrorCode.INVALID_AMOUNT, "amount must be positive");

        Result<Integer> mapped = failure.map(i -> i * 3);
        Result<Integer> flatMapped = failure.flatMap(i -> Result.success(i + 1));

        assertThat(mapped.isFailure()).isTrue();
        assertThat(flatMapped.isFailure()).isTrue();
        assertThat(mapped.error()).hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        assertThat(flatMapped.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void failureOrElseThrowThrowsIllegalState() {
        Result<Integer> failure = Result.failure(new DomainError(ErrorCode.INSUFFICIENT_FUNDS, "not enough"));
        assertThatThrownBy(failure::orElseThrow)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INSUFFICIENT_FUNDS");
    }

    @Test
    void failureFactoriesCarryCodeAndMessage() {
        Result<Object> failure = Result.failure(ErrorCode.NEGATIVE_BALANCE, "negative");
        assertThat(failure.isFailure()).isTrue();
        assertThat(failure.error()).hasValue(new DomainError(ErrorCode.NEGATIVE_BALANCE, "negative"));
    }
}
