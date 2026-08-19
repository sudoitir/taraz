package io.github.sudoitir.taraz.adapters.driven.persistence.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * ADR-0048: the translator matches by PostgreSQL constraint <em>name</em>, never by message text —
 * these fixtures deliberately give every case a generic, unrelated message so a text-based match would
 * fail this test even where a name-based one passes.
 */
class PersistenceFailureTranslatorTest {

    @Test
    void uniqueViolationOnProcessedTransactionPkIsTransactionIdConflict() {
        DataIntegrityViolationException e = uniqueViolation("pk_processed_transaction", "some unrelated message text");

        Result<Object> result = PersistenceFailureTranslator.translate(e);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().orElseThrow().code()).isEqualTo(ErrorCode.TRANSACTION_ID_CONFLICT);
    }

    @Test
    void uniqueViolationOnLedgerTransactionExternalIdIsTransactionIdConflict() {
        DataIntegrityViolationException e =
                uniqueViolation("uq_ledger_transaction_external_id", "some unrelated message text");

        Result<Object> result = PersistenceFailureTranslator.translate(e);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().orElseThrow().code()).isEqualTo(ErrorCode.TRANSACTION_ID_CONFLICT);
    }

    @Test
    void uniqueViolationOnAnUnrelatedConstraintIsNotTranslated() {
        DataIntegrityViolationException e = uniqueViolation("pk_account", "unrelated");

        assertThatThrownBy(() -> PersistenceFailureTranslator.translate(e)).isSameAs(e);
    }

    @Test
    void lockTimeoutIsConcurrencyConflict() {
        CannotAcquireLockException e = new CannotAcquireLockException("could not obtain lock");

        Result<Object> result = PersistenceFailureTranslator.translate(e);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().orElseThrow().code()).isEqualTo(ErrorCode.CONCURRENCY_CONFLICT);
    }

    @Test
    void queryTimeoutIsConcurrencyConflict() {
        QueryTimeoutException e = new QueryTimeoutException("statement timeout");

        Result<Object> result = PersistenceFailureTranslator.translate(e);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().orElseThrow().code()).isEqualTo(ErrorCode.CONCURRENCY_CONFLICT);
    }

    @Test
    void connectionPoolExhaustionIsConcurrencyConflict() {
        // JpaTransactionManager.doBegin() wraps a Hikari connection-timeout (ADR-0054) as this
        // TransactionException — not a DataAccessException — before any DAO-level translation runs.
        CannotCreateTransactionException e =
                new CannotCreateTransactionException("Could not open JPA EntityManager for transaction");

        Result<Object> result = PersistenceFailureTranslator.translate(e);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().orElseThrow().code()).isEqualTo(ErrorCode.CONCURRENCY_CONFLICT);
    }

    @Test
    void anUnrelatedDataAccessExceptionIsRethrownUnclassified() {
        UncategorizedDataAccessException e = new UncategorizedDataAccessException("something else broke", null) {};

        assertThatThrownBy(() -> PersistenceFailureTranslator.translate(e)).isSameAs(e);
    }

    /** Builds a {@link DataIntegrityViolationException} wrapping a PostgreSQL 23505 with the given constraint name. */
    private static DataIntegrityViolationException uniqueViolation(String constraintName, String message) {
        String raw = "SERROR\0C23505\0M" + message + "\0n" + constraintName + "\0\0";
        PSQLException psql = new PSQLException(new ServerErrorMessage(raw));
        return new DataIntegrityViolationException(message, psql);
    }
}
