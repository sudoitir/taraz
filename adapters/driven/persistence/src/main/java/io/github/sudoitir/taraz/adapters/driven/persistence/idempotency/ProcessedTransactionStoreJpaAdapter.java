package io.github.sudoitir.taraz.adapters.driven.persistence.idempotency;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.outbound.ProcessedTransactionStore;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * ADR-0021/0041: the authoritative idempotency record. {@link #find} is called only after the
 * relevant account rows are locked (ADR-0026), inside the {@code UnitOfWork} boundary — that ordering
 * is what makes concurrent duplicates serialize on the row lock instead of racing each other to this
 * table.
 */
@Repository
public class ProcessedTransactionStoreJpaAdapter implements ProcessedTransactionStore {

    private final EntityManager em;
    private final ProcessedOutcomeCodec codec;
    private final Clock clock;

    public ProcessedTransactionStoreJpaAdapter(EntityManager em, ProcessedOutcomeCodec codec, Clock clock) {
        this.em = Objects.requireNonNull(em, "em");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<CommandOutcome> find(TransactionId id) {
        ProcessedTransactionEntity entity = em.find(ProcessedTransactionEntity.class, id.value());
        return Optional.ofNullable(entity).map(e -> codec.fromJson(id, e.getOutcome()));
    }

    @Override
    public void record(TransactionId id, CommandOutcome outcome) {
        // The ledger_transaction row for this id was persisted moments earlier by
        // TransactionRepositoryJpaAdapter, in the same DB transaction — em's default AUTO flush mode
        // flushes pending changes before this query runs, so the not-yet-committed row is visible.
        UUID ledgerTransactionId = em.createQuery(
                        "SELECT e.id FROM LedgerTransactionEntity e WHERE e.externalId = :externalId", UUID.class)
                .setParameter("externalId", id.value())
                .getSingleResult();

        ProcessedTransactionEntity entity = new ProcessedTransactionEntity();
        entity.setTransactionId(id.value());
        entity.setRecordedAt(clock.instant());
        entity.setLedgerTransactionId(ledgerTransactionId);
        entity.setOutcome(codec.toJson(outcome));
        em.persist(entity);
    }
}
