/**
 * The authoritative idempotency record ({@link
 * io.github.sudoitir.taraz.core.application.ports.outbound.ProcessedTransactionStore}, ADR-0021/0041)
 * and its advisory fast path ({@link io.github.sudoitir.taraz.core.application.ports.outbound.IdempotencyGate}
 * on Valkey, ADR-0020).
 */
@NullMarked
package io.github.sudoitir.taraz.adapters.driven.persistence.idempotency;

import org.jspecify.annotations.NullMarked;
