package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;

/**
 * The advisory idempotency gate's answer (ADR-0021/0041). Deliberately has no "in progress" case: a
 * reader never has to interpret a partially-completed state. {@link Unknown} — including "the gate is
 * unreachable" — always falls through to the authoritative database path.
 */
public sealed interface GateDecision {

    /** This call is the first to see the transaction id; proceed to the authoritative path. */
    record Won() implements GateDecision {}

    /** The gate has seen a completed application of this transaction id; replay its outcome. */
    record AlreadyApplied(CommandOutcome outcome) implements GateDecision {}

    /** No answer either way (including gate unavailable); proceed to the authoritative path. */
    record Unknown() implements GateDecision {}
}
