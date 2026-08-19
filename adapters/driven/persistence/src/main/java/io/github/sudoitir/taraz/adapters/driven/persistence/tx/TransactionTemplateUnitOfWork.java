package io.github.sudoitir.taraz.adapters.driven.persistence.tx;

import io.github.sudoitir.taraz.core.application.ports.outbound.UnitOfWork;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR-0018/0040/0046: the transaction boundary a command handler opens at its true atomic unit —
 * exactly the body of the {@code inTransaction} call, never an annotation.
 *
 * <p><b>Isolation is READ COMMITTED</b> (ADR-0046), not the JDBC default merely left alone: correctness
 * comes from ADR-0026's row locks, not from snapshot isolation. Under READ COMMITTED, when a blocked
 * {@code SELECT ... FOR UPDATE} unblocks, PostgreSQL's {@code EvalPlanQual} re-reads the newest
 * <em>committed</em> row — exactly "the race loser queues, then decides against the updated balance."
 * REPEATABLE READ would instead throw a {@code 40001} serialization error on the loser, reintroducing
 * the retry-based concurrency model ADR-0026 replaced ADR-0017 to get away from.
 */
@Component
public final class TransactionTemplateUnitOfWork implements UnitOfWork {

    private final TransactionTemplate template;

    public TransactionTemplateUnitOfWork(PlatformTransactionManager txManager) {
        Objects.requireNonNull(txManager, "txManager");
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        t.setName("taraz.atomic-unit");
        this.template = t;
    }

    @Override
    public <T> Result<T> inTransaction(Supplier<Result<T>> work) {
        try {
            Result<T> result = template.execute(status -> {
                Result<T> outcome = work.get();
                if (outcome.isFailure()) {
                    // ADR-0040's explicit contract: Failure ⇒ rollback, communicated by returning a
                    // value, never by throwing. This is always the outermost (only) transaction, so
                    // this sets Spring's *local* rollback-only flag — commit() checks
                    // isLocalRollbackOnly() first and returns cleanly without committing, no
                    // UnexpectedRollbackException. That exception is only reachable from a *global*
                    // rollback-only path (an inner participating transaction), which never occurs
                    // here: one inTransaction call per handler invocation, nothing wraps it.
                    status.setRollbackOnly();
                }
                return outcome;
            });
            // template.execute's callback never returns null here (work always returns a Result),
            // but the compiler doesn't know that — this satisfies the nullness contract explicitly.
            return Objects.requireNonNull(result, "transactional work must return a Result");
        } catch (RuntimeException e) {
            return PersistenceFailureTranslator.translate(e);
        }
    }
}
