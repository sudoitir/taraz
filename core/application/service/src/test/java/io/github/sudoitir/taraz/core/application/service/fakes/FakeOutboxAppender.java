package io.github.sudoitir.taraz.core.application.service.fakes;

import io.github.sudoitir.taraz.core.application.ports.outbound.OutboxAppender;
import io.github.sudoitir.taraz.core.domain.common.DomainEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FakeOutboxAppender implements OutboxAppender {

    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(List<DomainEvent> newEvents) {
        events.addAll(newEvents);
    }

    public List<DomainEvent> events() {
        return Collections.unmodifiableList(events);
    }
}
