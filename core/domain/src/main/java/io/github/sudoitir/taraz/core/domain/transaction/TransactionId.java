package io.github.sudoitir.taraz.core.domain.transaction;

import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Client-supplied correlation id of a financial operation. The domain treats it as opaque — uniqueness /
 * idempotency is enforced by the command handler, Valkey and the DB unique constraint (ADR-0021/0034),
 * not here.
 */
public record TransactionId(String value) {

    public TransactionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            // Programmer error channel — the validating route is {@link #of}, which reports INVALID_TRANSACTION_ID.
            throw new IllegalArgumentException("transaction id must not be blank");
        }
    }

    /** The validating route for client-supplied input. */
    public static Result<TransactionId> of(@Nullable String value) {
        return value == null || value.isBlank()
                ? Result.failure(ErrorCode.INVALID_TRANSACTION_ID, "transaction id must not be blank")
                : Result.success(new TransactionId(value));
    }

    @Override
    public String toString() {
        return value;
    }
}
