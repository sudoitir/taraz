package io.github.sudoitir.taraz.adapters.driven.persistence.transaction;

import io.github.sudoitir.taraz.core.application.ports.outbound.TransactionRepository;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.transaction.LedgerEntry;
import io.github.sudoitir.taraz.core.domain.transaction.Transaction;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * ADR-0015/0037: persists a {@link Transaction} and its {@link LedgerEntry} legs as flat rows — three
 * explicit {@code em.persist} calls for a transfer (parent + two legs), never a cascade over a relation
 * graph. The surrogate {@code id} (ADR-0016/0047) is generated here with the domain's own
 * {@link IdGenerator} (UUIDv7), not by the database, so no round trip is needed to obtain it.
 */
@Repository
public class TransactionRepositoryJpaAdapter implements TransactionRepository {

    private final EntityManager em;
    private final TransactionPersistenceMapper mapper;
    private final IdGenerator ids;
    private final Clock clock;

    public TransactionRepositoryJpaAdapter(
            EntityManager em, TransactionPersistenceMapper mapper, IdGenerator ids, Clock clock) {
        this.em = Objects.requireNonNull(em, "em");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void save(Transaction transaction) {
        var now = clock.instant();
        UUID surrogateId = ids.newId();
        em.persist(mapper.toEntity(transaction, surrogateId, now));
        for (LedgerEntry entry : transaction.entries()) {
            em.persist(mapper.toEntity(entry, surrogateId, now));
        }
    }
}
