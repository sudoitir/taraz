package io.github.sudoitir.taraz.core.domain.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root base adding domain-event recording. {@link #pullDomainEvents()} is called once at the
 * transaction boundary by the application layer: it hands over a copy and clears, so a second call cannot
 * re-publish.
 *
 * <p>The backing list is a plain {@link ArrayList} on purpose — a concurrent collection would falsely
 * advertise shared mutability; real concurrency control lives in the persistence layer (ADR-0026).
 */
public abstract class AbstractAggregateRoot<ID> extends AbstractEntity<ID> {

    private final List<DomainEvent> events = new ArrayList<>();

    protected AbstractAggregateRoot(ID id) {
        super(id);
    }

    protected final void registerEvent(DomainEvent event) {
        events.add(Objects.requireNonNull(event, "event"));
    }

    /** Unmodifiable view of recorded-but-not-yet-pulled events. */
    public final List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(events);
    }

    /** Returns a copy of the recorded events, then clears the list — publication happens exactly once. */
    public final List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }
}
