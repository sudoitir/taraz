package io.github.sudoitir.taraz.core.application.ports;

/** Whether a command was applied for the first time or replayed from a prior application (ADR-0021/0034). */
public enum OutcomeStatus {
    APPLIED,
    REPLAYED
}
