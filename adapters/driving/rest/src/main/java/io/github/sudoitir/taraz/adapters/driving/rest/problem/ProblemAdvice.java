package io.github.sudoitir.taraz.adapters.driving.rest.problem;

import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Transport-level errors → RFC 7807 (ADR-0043). Domain failures never reach here — controllers fold
 * {@code Result} through {@link ProblemFactory}; this advice covers what the servlet/validation layer
 * raises before or around the controller, plus an opaque 500 fallback (nothing internal leaks; the
 * stack goes to the log, correlated by {@code flow_id}).
 *
 * <p>Ordered ahead of Boot 4's auto-configured {@code ProblemDetailsExceptionHandler} ({@code @Order(0)},
 * active under {@code spring.mvc.problemdetails.enabled=true}) so the mapped exceptions below carry our
 * stable {@code code}; anything unmapped still falls through to Boot's default problem rendering.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public final class ProblemAdvice {

    private final ProblemFactory problems;

    public ProblemAdvice(ProblemFactory problems) {
        this.problems = Objects.requireNonNull(problems, "problems");
    }

    /** The only required header in this API is {@code Idempotency-Key} (ADR-0043). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ProblemDetail> missingHeader(MissingRequestHeaderException ex) {
        return problems.of(
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSACTION_ID",
                "Missing required header",
                "Required header '%s' is missing".formatted(ex.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validationFailed(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(field ->
                        field.getField() + ": " + Objects.requireNonNullElse(field.getDefaultMessage(), "invalid"))
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        return problems.of(HttpStatus.BAD_REQUEST, codeFor(ex), "Validation failed", detail);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> malformedRequest(Exception ex) {
        return problems.of(
                HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request", "The request could not be read");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        log.error("unhandled request error [flow_id={}]", MDC.get("flow_id"), ex);
        return problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal error", "An unexpected error occurred");
    }

    /** Field-aware code: amount violations → INVALID_AMOUNT, id fields → INVALID_ACCOUNT_ID. */
    private static String codeFor(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(field -> switch (field.getField()) {
                    case "amount" -> "INVALID_AMOUNT";
                    case "sourceAccountId", "destinationAccountId", "accountId" -> "INVALID_ACCOUNT_ID";
                    default -> "VALIDATION_FAILED";
                })
                .findFirst()
                .orElse("VALIDATION_FAILED");
    }
}
