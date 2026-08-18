package io.github.sudoitir.taraz.core.application.service.fakes;

import io.github.sudoitir.taraz.core.application.ports.outbound.UnitOfWork;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runs {@code work} directly and releases any row locks {@link FakeAccountRepository} acquired on the
 * calling thread once the work completes — success, failure, or exception — mirroring a real DB
 * transaction's commit/rollback releasing its row locks. This is what lets the concurrency tests observe
 * genuine serialization on a contended account: the lock is held for the whole atomic unit, not just the
 * load.
 */
public final class FakeUnitOfWork implements UnitOfWork {

    private final FakeAccountRepository accounts;
    private volatile boolean lastRolledBack;

    public FakeUnitOfWork(FakeAccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Override
    public <T> Result<T> inTransaction(Supplier<Result<T>> work) {
        try {
            Result<T> result = work.get();
            lastRolledBack = result.isFailure();
            return result;
        } finally {
            accounts.releaseLocksHeldByCurrentThread();
        }
    }

    public boolean lastRolledBack() {
        return lastRolledBack;
    }
}
