package io.github.sudoitir.taraz.adapters.driving.rest.problem;

import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import java.net.URI;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Builds RFC 7807 {@link ProblemDetail} responses. Every problem carries a stable extension member
 * {@code code} — clients assert on codes, never on messages (ADR-0043). Domain failures arrive as
 * {@link DomainError} (folded from {@code Result} by controllers — results, not exceptions, ADR-0011);
 * the advice reuses this factory for transport-level errors.
 *
 * <p>Status mapping: client-fixable input → 400, missing resource → 404, valid-syntax business
 * rejection → 422, internal invariants → 500 (unreachable by design; if one ever fires it is a bug,
 * not a client error).
 */
@Component
public final class ProblemFactory {

    private static final Map<ErrorCode, HttpStatus> STATUS_BY_CODE;

    static {
        EnumMap<ErrorCode, HttpStatus> map = new EnumMap<>(ErrorCode.class);
        map.put(ErrorCode.INVALID_AMOUNT, HttpStatus.BAD_REQUEST);
        map.put(ErrorCode.INVALID_ACCOUNT_ID, HttpStatus.BAD_REQUEST);
        map.put(ErrorCode.INVALID_TRANSACTION_ID, HttpStatus.BAD_REQUEST);
        map.put(ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND);
        map.put(ErrorCode.INSUFFICIENT_FUNDS, HttpStatus.UNPROCESSABLE_CONTENT);
        map.put(ErrorCode.SAME_ACCOUNT_TRANSFER, HttpStatus.UNPROCESSABLE_CONTENT);
        map.put(ErrorCode.TRANSACTION_ID_CONFLICT, HttpStatus.CONFLICT);
        map.put(ErrorCode.CONCURRENCY_CONFLICT, HttpStatus.SERVICE_UNAVAILABLE);
        map.put(ErrorCode.NEGATIVE_BALANCE, HttpStatus.INTERNAL_SERVER_ERROR);
        map.put(ErrorCode.UNBALANCED_TRANSACTION, HttpStatus.INTERNAL_SERVER_ERROR);
        map.put(ErrorCode.INVALID_ENTRY_SHAPE, HttpStatus.INTERNAL_SERVER_ERROR);
        map.put(ErrorCode.COMPENSATION_TARGET_NOT_APPLIED, HttpStatus.INTERNAL_SERVER_ERROR);
        STATUS_BY_CODE = Map.copyOf(map);
    }

    /** Retry hint for {@link ErrorCode#CONCURRENCY_CONFLICT} (ADR-0048/0054) — a short, fixed budget: this
     * failure means a lock or connection wait was already exhausted, not that the system is down. */
    private static final long CONCURRENCY_CONFLICT_RETRY_AFTER_SECONDS = 1;

    /** Folds a domain failure into its problem response. */
    public ResponseEntity<ProblemDetail> toResponse(DomainError error) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(error.code(), HttpStatus.INTERNAL_SERVER_ERROR);
        ResponseEntity<ProblemDetail> response =
                of(status, error.code().name(), titleCase(error.code().name()), error.message());
        if (error.code() == ErrorCode.CONCURRENCY_CONFLICT) {
            return ResponseEntity.status(status)
                    .header("Retry-After", Long.toString(CONCURRENCY_CONFLICT_RETRY_AFTER_SECONDS))
                    .body(response.getBody());
        }
        return response;
    }

    /** Transport-level problem (bean validation, missing header, malformed body, fallback 500). */
    public ResponseEntity<ProblemDetail> of(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:taraz:problem:" + kebab(code)));
        problem.setTitle(title);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    private static String kebab(String codeName) {
        return codeName.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** {@code INSUFFICIENT_FUNDS} → {@code Insufficient funds}. */
    private static String titleCase(String codeName) {
        String kebab = kebab(codeName);
        return Character.toUpperCase(kebab.charAt(0)) + kebab.substring(1).replace('-', ' ');
    }
}
