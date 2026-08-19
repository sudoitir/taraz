package io.github.sudoitir.taraz.adapters.driven.persistence.account;

import io.github.sudoitir.taraz.core.application.ports.outbound.AccountRepository;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.stereotype.Repository;

/**
 * ADR-0015/0026/0042/0045: pessimistic row-locking implementation of {@link AccountRepository}.
 *
 * <p>{@link #lockAllInIdOrder} issues N <em>sequential</em> {@code SELECT ... FOR UPDATE} statements
 * in canonical {@link AccountId} order — never a single multi-row {@code WHERE id IN (...) ORDER BY id
 * FOR UPDATE} (ADR-0045). PostgreSQL does not document row-lock acquisition order under a multi-row
 * {@code FOR UPDATE} as a stable contract; ADR-0026's "deadlock impossible by design" claim must rest
 * on a property of this code, not of a query plan that could silently change.
 */
@Repository
public class AccountRepositoryJpaAdapter implements AccountRepository {

    private final EntityManager em;
    private final AccountPersistenceMapper mapper;
    private final Clock clock;

    /**
     * {@code em} is the shared, transaction-scoped {@link EntityManager} bean container's
     * {@code PersistenceConfiguration} exposes via {@code SharedEntityManagerCreator} — constructor
     * injection needs a real bean of this type; {@code @PersistenceContext} cannot target a
     * constructor parameter (JPA restricts it to fields and setter methods).
     */
    public AccountRepositoryJpaAdapter(EntityManager em, AccountPersistenceMapper mapper, Clock clock) {
        this.em = Objects.requireNonNull(em, "em");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result<List<Account>> lockAllInIdOrder(Collection<AccountId> ids) {
        // ADR-0042: the canonical comparator, not UUID's default signed one. TreeSet also dedupes —
        // a caller passing the same id twice cannot self-deadlock on a re-entrant row lock.
        List<AccountId> ordered = List.copyOf(new TreeSet<>(ids));

        List<Account> locked = new ArrayList<>(ordered.size());
        for (AccountId id : ordered) {
            // em.find (not getReference, which defers the SELECT and would never fire the lock).
            AccountEntity entity = em.find(AccountEntity.class, id.value(), LockModeType.PESSIMISTIC_WRITE);
            if (entity == null) {
                // Locks already taken are released when the enclosing transaction rolls back.
                return Result.failure(ErrorCode.ACCOUNT_NOT_FOUND, "unknown account: " + id);
            }
            locked.add(mapper.toDomain(entity));
        }
        return Result.success(locked);
    }

    @Override
    public void saveAll(List<Account> accounts) {
        var now = clock.instant();
        for (Account account : accounts) {
            // LockModeType.NONE: the already-locked case is served from the first-level cache with
            // zero SQL (lockAllInIdOrder loaded it in this same transaction); only the brand-new
            // account path (never locked — e.g. account creation) costs a SELECT here.
            AccountEntity managed = em.find(AccountEntity.class, account.id().value());
            if (managed == null) {
                em.persist(mapper.toNewEntity(account, now));
            } else {
                managed.getBalance().setMinorUnits(account.balance().minorUnits());
                managed.setUpdatedAt(now);
            }
        }
    }
}
