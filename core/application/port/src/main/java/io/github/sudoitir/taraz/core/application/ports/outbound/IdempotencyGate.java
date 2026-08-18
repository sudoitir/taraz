package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;

/**
 * The advisory, non-authoritative fast path for idempotency (ADR-0021/0041). {@link ProcessedTransactionStore}
 * remains the authoritative check; this gate only saves the round trip to the database on the common
 * case. Implementations MUST fail open: any failure to reach the backing store returns
 * {@link GateDecision.Unknown}, never an exception and never a false {@link GateDecision.AlreadyApplied}.
 */
public interface IdempotencyGate {

    GateDecision tryBegin(TransactionId id);

    /** Best-effort; called after the atomic unit commits. A failure here must not fail the command. */
    void publishOutcome(TransactionId id, CommandOutcome outcome);

    /** Best-effort; called when the atomic unit fails, so a retry is not blocked by a stale reservation. */
    void release(TransactionId id);
}
