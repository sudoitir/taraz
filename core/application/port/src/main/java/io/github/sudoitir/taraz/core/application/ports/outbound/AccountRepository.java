package io.github.sudoitir.taraz.core.application.ports.outbound;

import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.Collection;
import java.util.List;

/**
 * Loads and persists {@link Account} aggregates within the {@link UnitOfWork} boundary.
 *
 * <p>{@link #lockAllInIdOrder} owns the lock-acquisition order itself — it sorts {@code ids} by
 * {@link AccountId}'s canonical comparator (ADR-0042) before locking, so no caller can defeat
 * ADR-0026's deadlock-freedom by passing ids in the "wrong" order. Implementations acquire a
 * {@code SELECT ... FOR UPDATE} row lock on every id, in that order, within the current transaction.
 */
public interface AccountRepository {

    /**
     * Locks and loads every account in {@code ids}, in ascending {@link AccountId} order. Fails with
     * {@link io.github.sudoitir.taraz.core.domain.common.ErrorCode#ACCOUNT_NOT_FOUND} if any id has no
     * corresponding account; the caller (inside {@link UnitOfWork#inTransaction}) is expected to
     * propagate the failure so the transaction rolls back rather than saving a partial result.
     */
    Result<List<Account>> lockAllInIdOrder(Collection<AccountId> ids);

    /**
     * Persists every account in {@code accounts}, handling both cases an implementation must
     * distinguish: an account already locked in this transaction by {@link #lockAllInIdOrder} (an
     * update of its balance), and a brand-new account that was never locked because it does not yet
     * exist (an insert — the account-opening use case's path, which has no ids to lock). Passing an
     * account that exists but was not locked in the current transaction is a caller error.
     */
    void saveAll(List<Account> accounts);
}
