package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * `.claude/rules/challenge-idempotency.md`: duplicate requests may arrive <em>concurrently</em>, not
 * just as sequential retries. {@code .claude/rules/challenge-idempotency.md} — idempotency must hold
 * under races. Because {@link io.github.sudoitir.taraz.core.application.ports.outbound.GateDecision.Won}
 * is unreachable in the read-through design (ADR-0041/D4), every one of these racing duplicates passes
 * the Valkey gate as {@code Unknown} and only the account row lock + the {@code processed_transaction}
 * natural-key PK (last guard) decide the single winner — this proves that guard holds under a real race,
 * not just the in-memory fakes.
 */
@TarazIntegrationTest
class IdempotencyConcurrentIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void concurrentDuplicatesOfTheSameTransactionIdApplyExactlyOnce() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        String txId = "TX-DUP-" + UUID.randomUUID();

        int n = 50;
        List<Result<CommandOutcome>> results = TestConcurrency.runConcurrently(
                n, i -> credit.handle(new CreditCommand(account.toString(), 500, txId)));

        assertThat(results)
                .allSatisfy(r -> assertThat(r.isSuccess())
                        .as("every duplicate must resolve, applied or replayed, never fail")
                        .isTrue());

        long applied = results.stream()
                .filter(r -> r.orElseThrow().status() == OutcomeStatus.APPLIED)
                .count();
        long replayed = results.stream()
                .filter(r -> r.orElseThrow().status() == OutcomeStatus.REPLAYED)
                .count();
        assertThat(applied).as("exactly one racing duplicate applies").isEqualTo(1);
        assertThat(replayed).isEqualTo(n - 1);

        BalanceView balance =
                getBalance.handle(new GetBalanceQuery(account.toString())).orElseThrow();
        assertThat(balance.balance()).isEqualTo(Money.of(500).orElseThrow());
    }
}
