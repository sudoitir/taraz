package io.github.sudoitir.taraz.core.domain.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractAggregateRootTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");

    private record TestEvent(String eventType, Instant occurredAt, String transactionId) implements DomainEvent {}

    private static final class TestAggregate extends AbstractAggregateRoot<String> {
        TestAggregate(String id) {
            super(id);
        }

        void record(DomainEvent event) {
            registerEvent(event);
        }
    }

    @Test
    void domainEventsReturnsRecordedEventsAsUnmodifiableView() {
        TestAggregate aggregate = new TestAggregate("id-1");
        aggregate.record(new TestEvent("test.happened", AT, "TX-1"));

        List<DomainEvent> events = aggregate.domainEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("test.happened");
        assertThatThrownBy(() -> events.add(new TestEvent("test.other", AT, "TX-2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pullDomainEventsReturnsCopyThenClears() {
        TestAggregate aggregate = new TestAggregate("id-1");
        aggregate.record(new TestEvent("test.one", AT, "TX-1"));
        aggregate.record(new TestEvent("test.two", AT, "TX-2"));

        List<DomainEvent> pulled = aggregate.pullDomainEvents();

        assertThat(pulled).hasSize(2);
        assertThat(aggregate.domainEvents()).isEmpty();
        assertThat(aggregate.pullDomainEvents()).isEmpty();
    }

    @Test
    void pulledCopyIsDetachedFromLaterRegistrations() {
        TestAggregate aggregate = new TestAggregate("id-1");
        aggregate.record(new TestEvent("test.one", AT, "TX-1"));

        List<DomainEvent> pulled = aggregate.pullDomainEvents();
        aggregate.record(new TestEvent("test.two", AT, "TX-2"));

        assertThat(pulled).hasSize(1);
        assertThat(aggregate.domainEvents()).hasSize(1);
    }
}
