package io.github.sudoitir.taraz.core.domain.transaction.event;

import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionType;
import java.time.Instant;

/**
 * The only route to transaction events (ADR-0005). Event builders are package-private so construction
 * cannot bypass this factory.
 */
public final class TransactionEvents {

    private TransactionEvents() {}

    public static TransactionPosted posted(TransactionId transactionId, TransactionType type, Instant at) {
        return TransactionPosted.builder()
                .eventType(TransactionPosted.EVENT_TYPE)
                .occurredAt(at)
                .transactionId(transactionId.value())
                .type(type)
                .build();
    }

    public static TransactionCompensated compensated(
            TransactionId compensationId, TransactionId compensates, Instant at) {
        return TransactionCompensated.builder()
                .eventType(TransactionCompensated.EVENT_TYPE)
                .occurredAt(at)
                .transactionId(compensationId.value())
                .compensates(compensates)
                .build();
    }
}
