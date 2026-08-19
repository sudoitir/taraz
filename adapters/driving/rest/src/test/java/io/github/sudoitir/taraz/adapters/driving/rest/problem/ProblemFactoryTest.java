package io.github.sudoitir.taraz.adapters.driving.rest.problem;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** ADR-0048: the two persistence-boundary conflict codes map to typed, non-500 responses. */
class ProblemFactoryTest {

    private final ProblemFactory problems = new ProblemFactory();

    @Test
    void transactionIdConflictMapsTo409() {
        ResponseEntity<ProblemDetail> response =
                problems.toResponse(new DomainError(ErrorCode.TRANSACTION_ID_CONFLICT, "conflict"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = Objects.requireNonNull(response.getBody());
        assertThat(body.getProperties()).containsEntry("code", "TRANSACTION_ID_CONFLICT");
    }

    @Test
    void concurrencyConflictMapsTo503WithRetryAfter() {
        ResponseEntity<ProblemDetail> response =
                problems.toResponse(new DomainError(ErrorCode.CONCURRENCY_CONFLICT, "no capacity"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst("Retry-After")).isNotBlank();
        ProblemDetail body = Objects.requireNonNull(response.getBody());
        assertThat(body.getProperties()).containsEntry("code", "CONCURRENCY_CONFLICT");
    }

    @Test
    void otherCodesCarryNoRetryAfter() {
        ResponseEntity<ProblemDetail> response =
                problems.toResponse(new DomainError(ErrorCode.INSUFFICIENT_FUNDS, "not enough"));

        assertThat(response.getHeaders().getFirst("Retry-After")).isNull();
    }
}
