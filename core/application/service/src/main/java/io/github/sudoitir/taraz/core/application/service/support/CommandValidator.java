package io.github.sudoitir.taraz.core.application.service.support;

import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Runs {@link Validator} over a command's structural shape (blank/absent, non-positive) before any
 * handler logic executes, and maps the first relevant violation to the matching {@link ErrorCode}
 * (ADR-0034). This is deliberately shallow — presence and format only. Deeper domain validity (a
 * malformed but non-blank id, an unknown account) is checked afterward by the domain's own validating
 * factories, which return the same {@link ErrorCode} catalog.
 *
 * <p>Property-path mapping is deterministic regardless of {@link Set} iteration order: any violation
 * whose path names an account id wins first (a self-transfer's shared field name shows up on both
 * {@code sourceAccountId} and {@code destinationAccountId}), then {@code transactionId}, then
 * {@code amount} — matching the order a caller most likely wants to see when several constraints fail
 * at once.
 */
@Component
public final class CommandValidator {

    private final Validator validator;

    public CommandValidator(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public <T> Result<T> validate(T command) {
        Set<ConstraintViolation<T>> violations = validator.validate(command);
        if (violations.isEmpty()) {
            return Result.success(command);
        }
        return Result.failure(errorCodeFor(violations), messageFor(violations));
    }

    private static <T> ErrorCode errorCodeFor(Set<ConstraintViolation<T>> violations) {
        if (matches(violations, "accountid")) {
            return ErrorCode.INVALID_ACCOUNT_ID;
        }
        if (matches(violations, "transactionid")) {
            return ErrorCode.INVALID_TRANSACTION_ID;
        }
        return ErrorCode.INVALID_AMOUNT;
    }

    private static <T> boolean matches(Set<ConstraintViolation<T>> violations, String propertyPathFragment) {
        return violations.stream()
                .anyMatch(v ->
                        v.getPropertyPath().toString().toLowerCase(Locale.ROOT).contains(propertyPathFragment));
    }

    private static <T> String messageFor(Set<ConstraintViolation<T>> violations) {
        ConstraintViolation<T> first = violations.iterator().next();
        return first.getPropertyPath() + " " + first.getMessage();
    }
}
