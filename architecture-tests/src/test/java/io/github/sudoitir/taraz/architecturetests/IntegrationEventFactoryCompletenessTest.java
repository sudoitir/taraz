package io.github.sudoitir.taraz.architecturetests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.github.sudoitir.taraz.adapters.driven.messaging.outbox.IntegrationEventFactory;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.account.event.AccountEvents;
import io.github.sudoitir.taraz.core.domain.common.AbstractDomainEvent;
import io.github.sudoitir.taraz.core.domain.common.DomainEvent;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionType;
import io.github.sudoitir.taraz.core.domain.transaction.event.TransactionEvents;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * ADR-0009/0050: {@link IntegrationEventFactory}'s dispatch must map every concrete domain event type
 * — a financial event that falls through to the {@code default} branch is silently dropped from the
 * outbox, not merely un-tested. This test has two halves: (1) enumerate every concrete
 * {@link AbstractDomainEvent} subclass on the classpath via ArchUnit's importer and assert this test
 * itself instantiates one of each (so a new event type added to the domain without updating this test
 * fails loudly, forcing the factory to be updated too); (2) feed one instance of every known type
 * through the real factory and assert it never reaches the {@code default} throw.
 */
class IntegrationEventFactoryCompletenessTest {

    private static final Instant AT = Instant.parse("2026-08-19T10:00:00Z");
    private static final UuidV7IdGenerator IDS = new UuidV7IdGenerator();

    @Test
    void everyConcreteDomainEventClassIsCoveredByThisTestsFixtureList() {
        JavaClasses classes = new ClassFileImporter().importPackages("io.github.sudoitir.taraz.core.domain");
        Set<String> concreteDomainEventClasses = classes.stream()
                .filter(c -> c.isAssignableTo(AbstractDomainEvent.class))
                .filter(c -> !c.getModifiers().contains(JavaModifier.ABSTRACT))
                .map(JavaClass::getName)
                .collect(Collectors.toSet());

        Set<String> namesThisTestInstantiates =
                allKnownEvents().stream().map(e -> e.getClass().getName()).collect(Collectors.toSet());

        assertThat(namesThisTestInstantiates)
                .as("every concrete DomainEvent subclass must have a fixture instance in this test, "
                        + "so IntegrationEventFactory's completeness is actually exercised below")
                .containsExactlyInAnyOrderElementsOf(concreteDomainEventClasses);
    }

    @Test
    void factoryMapsEveryKnownEventTypeWithoutFallingThroughToDefault() {
        IntegrationEventFactory factory = new IntegrationEventFactory();
        for (DomainEvent event : allKnownEvents()) {
            factory.toIntegrationEvent(event, IDS.newId().toString(), null);
        }
    }

    @Test
    void anUnmappedEventTypeThrowsRatherThanBeingSilentlyDropped() {
        IntegrationEventFactory factory = new IntegrationEventFactory();
        DomainEvent unmapped = new DomainEvent() {
            @Override
            public String eventType() {
                return "test.unmapped";
            }

            @Override
            public Instant occurredAt() {
                return AT;
            }

            @Override
            public @Nullable String transactionId() {
                return null;
            }
        };

        assertThatThrownBy(() -> factory.toIntegrationEvent(unmapped, "id", null))
                .isInstanceOf(IllegalStateException.class);
    }

    private static List<DomainEvent> allKnownEvents() {
        AccountId account = new AccountId(IDS.newId());
        TransactionId txId = new TransactionId("TX-1");
        Money amount = Money.of(100).orElseThrow();

        return List.of(
                AccountEvents.opened(account, Money.ZERO, AT),
                AccountEvents.credited(account, amount, amount, txId.value(), AT),
                AccountEvents.debited(account, amount, Money.ZERO, txId.value(), AT),
                TransactionEvents.posted(txId, TransactionType.TRANSFER, AT),
                TransactionEvents.compensated(new TransactionId("TX-1-REV"), txId, AT));
    }
}
