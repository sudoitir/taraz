package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.function.Supplier;

/**
 * The transaction boundary a command handler opens at its true atomic unit (ADR-0018, ADR-0040). A
 * {@link io.github.sudoitir.taraz.core.domain.common.Result.Failure} returned by {@code work} rolls the
 * transaction back; a {@link io.github.sudoitir.taraz.core.domain.common.Result.Success} commits it.
 * {@code work} must contain no external I/O (ADR-0021/0026): the idempotency gate runs before this call,
 * never inside it.
 */
public interface UnitOfWork {
    <T> Result<T> inTransaction(Supplier<Result<T>> work);
}
