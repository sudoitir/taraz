package io.github.sudoitir.taraz.core.application.service.fakes;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.outbound.GateDecision;
import io.github.sudoitir.taraz.core.application.ports.outbound.IdempotencyGate;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three modes exercise the crash/failure matrix design.md describes: {@link Mode#NORMAL} behaves like a
 * healthy Valkey-backed gate; {@link Mode#ALWAYS_UNKNOWN} models an unreachable gate (D7 requires
 * exactly-once to still hold via the authoritative database path); {@link Mode#THROWS} models the gate
 * call itself failing, to document (not mask) how a handler reacts when a port breaks its contract.
 */
public final class FakeIdempotencyGate implements IdempotencyGate {

    public enum Mode {
        NORMAL,
        ALWAYS_UNKNOWN,
        THROWS
    }

    private final Mode mode;
    private final Set<TransactionId> won = ConcurrentHashMap.newKeySet();
    private final Map<TransactionId, CommandOutcome> applied = new ConcurrentHashMap<>();

    public FakeIdempotencyGate(Mode mode) {
        this.mode = mode;
    }

    @Override
    public synchronized GateDecision tryBegin(TransactionId id) {
        if (mode == Mode.THROWS) {
            throw new IllegalStateException("gate unreachable");
        }
        if (mode == Mode.ALWAYS_UNKNOWN) {
            return new GateDecision.Unknown();
        }
        CommandOutcome outcome = applied.get(id);
        if (outcome != null) {
            return new GateDecision.AlreadyApplied(outcome);
        }
        if (won.add(id)) {
            return new GateDecision.Won();
        }
        // Another caller already won and has not published yet — an advisory gate has no answer here.
        return new GateDecision.Unknown();
    }

    @Override
    public void publishOutcome(TransactionId id, CommandOutcome outcome) {
        if (mode == Mode.NORMAL) {
            applied.put(id, outcome);
        }
    }

    @Override
    public void release(TransactionId id) {
        won.remove(id);
    }
}
