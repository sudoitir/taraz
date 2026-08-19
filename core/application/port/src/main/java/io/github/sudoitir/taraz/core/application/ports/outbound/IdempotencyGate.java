package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;

/**
 * The advisory, non-authoritative fast path for idempotency (ADR-0021/0041). {@link ProcessedTransactionStore}
 * remains the authoritative check; this gate only saves the round trip to the database on the common
 * case. Implementations MUST fail open: any failure to reach the backing store returns
 * {@link GateDecision.Unknown}, never an exception and never a false {@link GateDecision.AlreadyApplied}.
 *
 * <p>An ADR-0041-compliant implementation is a pure read-through cache, not a reservation: it never
 * writes a placeholder before the atomic unit completes, so {@link GateDecision.Won} is unreachable in
 * practice — every caller in this codebase treats {@code Won} and {@link GateDecision.Unknown}
 * identically. {@code Won} remains in the sealed type for a future gate implementation that does
 * reserve, but nothing here relies on ever receiving it.
 */
public interface IdempotencyGate {

    GateDecision tryBegin(TransactionId id);

    /** Best-effort; called after the atomic unit commits. A failure here must not fail the command. */
    void publishOutcome(TransactionId id, CommandOutcome outcome);

    /**
     * Best-effort; called when the atomic unit fails. For a read-through cache implementation this is
     * a plain eviction of any cached entry for {@code id} — there is no reservation to release, since
     * none was written. Note this also fires when a duplicate is rejected with
     * {@code TRANSACTION_ID_CONFLICT} (ADR-0041/0048): the rejected caller's release evicts whatever
     * the original winner may have cached, which is harmless — the next lookup degrades to
     * {@link GateDecision.Unknown} and falls through to the authoritative database check.
     */
    void release(TransactionId id);
}
