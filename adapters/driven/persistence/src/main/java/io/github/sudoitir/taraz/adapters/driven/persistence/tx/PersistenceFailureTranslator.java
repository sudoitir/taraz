package io.github.sudoitir.taraz.adapters.driven.persistence.tx;

import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * ADR-0048: translates persistence-layer failures thrown out of {@link TransactionTemplateUnitOfWork}
 * into typed {@link Result.Failure} values, matching on the PostgreSQL <em>constraint name</em> —
 * never on exception message text, which is not a stable contract across PostgreSQL versions or
 * locales. Anything unmatched is rethrown, so it reaches the REST layer's opaque 500 fallback exactly
 * as an unclassified bug should.
 */
final class PersistenceFailureTranslator {

    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    /**
     * ADR-0041's last guard: the same {@code transactionId} resubmitted with different parameters
     * takes different account locks, so the application-level {@code processed.find} check (run
     * after locking) never sees it — the row it would have inserted collides with one of these
     * constraints at commit instead. Names must match the changesets exactly (guarded by
     * {@code SchemaIT}); a rename here without updating the migration silently downgrades a 409 into
     * an opaque 500.
     */
    private static final String PK_PROCESSED_TRANSACTION = "pk_processed_transaction";

    private static final String UQ_LEDGER_TRANSACTION_EXTERNAL_ID = "uq_ledger_transaction_external_id";

    private PersistenceFailureTranslator() {}

    static <T> Result<T> translate(RuntimeException e) {
        if (e instanceof DataIntegrityViolationException dive && isTransactionIdConflict(dive)) {
            return Result.failure(
                    ErrorCode.TRANSACTION_ID_CONFLICT, "transaction id already applied with different parameters");
        }
        if (isConcurrencyConflict(e)) {
            return Result.failure(
                    ErrorCode.CONCURRENCY_CONFLICT,
                    "could not acquire an account lock or a database connection in time");
        }
        throw e;
    }

    private static boolean isConcurrencyConflict(RuntimeException e) {
        return e instanceof CannotAcquireLockException
                || e instanceof PessimisticLockingFailureException
                || e instanceof QueryTimeoutException
                || e instanceof TransientDataAccessResourceException
                // Hikari connection-timeout (ADR-0054) surfaces here, not as a DataAccessException:
                // JpaTransactionManager.doBegin() wraps a failed getConnection() as
                // CannotCreateTransactionException before any DAO-level translation ever runs. Without
                // this arm, pool exhaustion would escape the Result algebra as a raw unchecked
                // TransactionException instead of the typed 503 this class exists to produce.
                || e instanceof CannotCreateTransactionException;
    }

    private static boolean isTransactionIdConflict(DataAccessException e) {
        return findPostgresError(e)
                .filter(p -> SQLSTATE_UNIQUE_VIOLATION.equals(p.getSQLState()))
                .map(PSQLException::getServerErrorMessage)
                .map(ServerErrorMessage::getConstraint)
                .filter(constraint -> PK_PROCESSED_TRANSACTION.equals(constraint)
                        || UQ_LEDGER_TRANSACTION_EXTERNAL_ID.equals(constraint))
                .isPresent();
    }

    private static Optional<PSQLException> findPostgresError(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof PSQLException psql) {
                return Optional.of(psql);
            }
            cause = cause.getCause();
        }
        return Optional.empty();
    }
}
