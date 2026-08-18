package io.github.sudoitir.taraz.core.application.service.fakes;

import io.github.sudoitir.taraz.core.application.ports.outbound.AccountRepository;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * In-memory {@link AccountRepository} modeling row-level locking: one {@link ReentrantLock} per account,
 * acquired in the ascending {@link AccountId} order the real port promises (ADR-0026/0042). Locks are
 * held until {@link #releaseLocksHeldByCurrentThread()} is called — {@link FakeUnitOfWork} calls it once
 * the enclosing unit of work ends, mirroring a real DB transaction's commit/rollback releasing its row
 * locks — so the concurrency tests observe genuine serialization on a contended account, not just
 * serialization of the load step.
 *
 * <p>{@link #lockAllInIdOrder} always hands back a fresh {@link Account} instance reconstituted from the
 * stored balance, so an in-place mutation by the caller is invisible to this store until {@link #saveAll}
 * is called — the same reason a real repository never needs to "roll back" a partially mutated aggregate.
 */
public final class FakeAccountRepository implements AccountRepository {

    private final Map<AccountId, Money> balances = new ConcurrentHashMap<>();
    private final Map<AccountId, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final ThreadLocal<List<ReentrantLock>> heldByThisThread = ThreadLocal.withInitial(ArrayList::new);

    public void seed(AccountId id, Money balance) {
        balances.put(id, balance);
        locks.put(id, new ReentrantLock());
    }

    @Override
    public Result<List<Account>> lockAllInIdOrder(Collection<AccountId> ids) {
        List<AccountId> ordered = new ArrayList<>(new TreeSet<>(ids));
        for (AccountId id : ordered) {
            ReentrantLock lock = locks.get(id);
            if (lock == null) {
                return Result.failure(ErrorCode.ACCOUNT_NOT_FOUND, "unknown account: " + id);
            }
            lock.lock();
            heldByThisThread.get().add(lock);
        }
        List<Account> loaded = new ArrayList<>();
        for (AccountId id : ordered) {
            Money balance = Objects.requireNonNull(balances.get(id), "balance present since lock succeeded");
            loaded.add(Account.reconstitute(id, balance).orElseThrow());
        }
        return Result.success(loaded);
    }

    @Override
    public void saveAll(List<Account> accounts) {
        for (Account account : accounts) {
            balances.put(account.id(), account.balance());
        }
    }

    /** Releases every row lock this thread acquired via {@link #lockAllInIdOrder}, in acquisition order. */
    public void releaseLocksHeldByCurrentThread() {
        List<ReentrantLock> held = heldByThisThread.get();
        for (int i = held.size() - 1; i >= 0; i--) {
            held.get(i).unlock();
        }
        held.clear();
    }

    public @Nullable Money balanceOf(AccountId id) {
        return balances.get(id);
    }
}
