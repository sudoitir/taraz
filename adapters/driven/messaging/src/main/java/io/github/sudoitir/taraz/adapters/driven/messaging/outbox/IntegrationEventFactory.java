package io.github.sudoitir.taraz.adapters.driven.messaging.outbox;

import io.github.sudoitir.taraz.adapters.driven.messaging.contract.AccountCreditedV1;
import io.github.sudoitir.taraz.adapters.driven.messaging.contract.AccountDebitedV1;
import io.github.sudoitir.taraz.adapters.driven.messaging.contract.AccountOpenedV1;
import io.github.sudoitir.taraz.adapters.driven.messaging.contract.IntegrationEventEnvelope;
import io.github.sudoitir.taraz.adapters.driven.messaging.contract.TransactionCompensatedV1;
import io.github.sudoitir.taraz.adapters.driven.messaging.contract.TransactionPostedV1;
import io.github.sudoitir.taraz.core.domain.account.event.AccountCredited;
import io.github.sudoitir.taraz.core.domain.account.event.AccountDebited;
import io.github.sudoitir.taraz.core.domain.account.event.AccountOpened;
import io.github.sudoitir.taraz.core.domain.common.DomainEvent;
import io.github.sudoitir.taraz.core.domain.transaction.event.TransactionCompensated;
import io.github.sudoitir.taraz.core.domain.transaction.event.TransactionPosted;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * ADR-0009/0050: builds the public {@link IntegrationEventEnvelope} from an internal {@code
 * DomainEvent}. Dispatch is an exhaustive pattern-matching {@code switch} — {@code DomainEvent} is not
 * sealed, so a {@code default} branch is mandatory, and it throws rather than silently dropping a
 * financial event: an unmapped event type is a bug that must fail loudly, not a value that
 * disappears. {@link IntegrationEventFactoryCompletenessTest} (architecture-tests) guards this by
 * enumerating every concrete domain event class and asserting this factory maps it.
 */
@Component
public final class IntegrationEventFactory {

    /** Aggregate type for account-scoped events — also the {@code taraz.account.v1} routing key (ADR-0051). */
    public static final String AGGREGATE_TYPE_ACCOUNT = "account";

    /** Aggregate type for transaction-scoped events — also the {@code taraz.transaction.v1} routing key. */
    public static final String AGGREGATE_TYPE_TRANSACTION = "transaction";

    public IntegrationEventEnvelope toIntegrationEvent(
            DomainEvent event, String eventId, @Nullable String correlationId) {
        String occurredAt = event.occurredAt().toString();
        return switch (event) {
            case AccountOpened e ->
                envelope(
                        eventId,
                        e.eventType(),
                        AGGREGATE_TYPE_ACCOUNT,
                        e.accountId().value().toString(),
                        e.transactionId(),
                        correlationId,
                        occurredAt,
                        new AccountOpenedV1(
                                e.accountId().value().toString(),
                                e.balance().minorUnits().toPlainString()));
            case AccountCredited e ->
                envelope(
                        eventId,
                        e.eventType(),
                        AGGREGATE_TYPE_ACCOUNT,
                        e.accountId().value().toString(),
                        e.transactionId(),
                        correlationId,
                        occurredAt,
                        new AccountCreditedV1(
                                e.accountId().value().toString(),
                                e.amount().minorUnits().toPlainString(),
                                e.balanceAfter().minorUnits().toPlainString()));
            case AccountDebited e ->
                envelope(
                        eventId,
                        e.eventType(),
                        AGGREGATE_TYPE_ACCOUNT,
                        e.accountId().value().toString(),
                        e.transactionId(),
                        correlationId,
                        occurredAt,
                        new AccountDebitedV1(
                                e.accountId().value().toString(),
                                e.amount().minorUnits().toPlainString(),
                                e.balanceAfter().minorUnits().toPlainString()));
            case TransactionPosted e ->
                envelope(
                        eventId,
                        e.eventType(),
                        AGGREGATE_TYPE_TRANSACTION,
                        Objects.requireNonNull(
                                e.transactionId(), "TransactionPosted always carries its own transaction id"),
                        e.transactionId(),
                        correlationId,
                        occurredAt,
                        new TransactionPostedV1(
                                Objects.requireNonNull(e.transactionId()),
                                e.type().name()));
            case TransactionCompensated e ->
                envelope(
                        eventId,
                        e.eventType(),
                        AGGREGATE_TYPE_TRANSACTION,
                        Objects.requireNonNull(
                                e.transactionId(), "TransactionCompensated always carries its own transaction id"),
                        e.transactionId(),
                        correlationId,
                        occurredAt,
                        new TransactionCompensatedV1(
                                Objects.requireNonNull(e.transactionId()),
                                e.compensates().value()));
            default ->
                throw new IllegalStateException("no integration event contract mapped for " + event.getClass() + " ("
                        + event.eventType() + ")");
        };
    }

    /** The Kafka topic a mapped event's aggregate type routes to (ADR-0051). */
    public static String topicFor(String aggregateType) {
        return switch (aggregateType) {
            case AGGREGATE_TYPE_ACCOUNT -> "taraz.account.v1";
            case AGGREGATE_TYPE_TRANSACTION -> "taraz.transaction.v1";
            default -> throw new IllegalStateException("no topic routing for aggregate type: " + aggregateType);
        };
    }

    private static IntegrationEventEnvelope envelope(
            String eventId,
            String eventType,
            String aggregateType,
            String aggregateId,
            @Nullable String transactionId,
            @Nullable String correlationId,
            String occurredAt,
            Object data) {
        return new IntegrationEventEnvelope(
                eventId, eventType, "1", aggregateType, aggregateId, transactionId, correlationId, occurredAt, data);
    }
}
