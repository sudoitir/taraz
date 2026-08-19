package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADR-0041/0048 (design.md D7): the same {@code transactionId} reused for two <em>disjoint</em> account
 * sets can't be caught by the application-level {@code processed.find} replay check — that check only
 * ever sees a committed row from a lock it shares, and these two operations lock different accounts, so
 * neither serializes behind the other. The last guard is the {@code pk_processed_transaction} PK: one
 * insert wins, the other collides and is translated to a typed {@code 409 TRANSACTION_ID_CONFLICT} — not
 * a leaked exception. Barrier-synchronized starts (`.claude/rules/challenge-testing.md`) make the race
 * window reliable: both operations reach their own {@code processed.record} insert before either commits.
 */
@TarazIntegrationTest
class TransactionIdConflictIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Test
    void sameTransactionIdOnDisjointAccountsYieldsTypedConflictNotALeakedException() throws Exception {
        AccountId accountA = createAccount.handle().orElseThrow().accountId();
        AccountId accountB = createAccount.handle().orElseThrow().accountId();
        String txId = "TX-CONFLICT-" + UUID.randomUUID();

        List<Result<CommandOutcome>> results = TestConcurrency.runConcurrently(
                2,
                i -> i == 0
                        ? credit.handle(new CreditCommand(accountA.toString(), 100, txId))
                        : credit.handle(new CreditCommand(accountB.toString(), 200, txId)));

        long applied = results.stream()
                .filter(r -> r.isSuccess() && r.orElseThrow().status() == OutcomeStatus.APPLIED)
                .count();
        long conflicted = results.stream()
                .filter(r -> r.isFailure() && r.error().orElseThrow().code() == ErrorCode.TRANSACTION_ID_CONFLICT)
                .count();

        assertThat(applied)
                .as("exactly one of the two disjoint-account operations applies")
                .isEqualTo(1);
        assertThat(conflicted)
                .as("the other must surface as a typed 409, never a leaked exception or a silent double-apply")
                .isEqualTo(1);
    }
}
