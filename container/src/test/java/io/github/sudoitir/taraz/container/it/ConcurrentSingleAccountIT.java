package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitUseCase;
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
 * The challenge's reference concurrency scenario (`.claude/rules/challenge-concurrency.md`): balance
 * 100,000, 1000 concurrent operations, exact final balance — proven here against real PostgreSQL row
 * locks (ADR-0026/0045), not the in-memory fake {@code ConcurrencySuiteTest} already proves handler
 * logic against.
 */
@TarazIntegrationTest
class ConcurrentSingleAccountIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private DebitUseCase debit;

    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void thousandConcurrentCreditsAndDebitsLeaveTheExactExpectedBalance() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        credit.handle(new CreditCommand(account.toString(), 100_000, "SEED-" + UUID.randomUUID()))
                .orElseThrow();

        int perDirection = 500;
        int n = perDirection * 2;
        List<Result<CommandOutcome>> results = TestConcurrency.runConcurrentlyRetryingOnBackpressure(n, i -> {
            String txId = "TX-" + i + "-" + UUID.randomUUID();
            return () -> i % 2 == 0
                    ? credit.handle(new CreditCommand(account.toString(), 100, txId))
                    : debit.handle(new DebitCommand(account.toString(), 100, txId));
        });

        assertThat(results)
                .allSatisfy(r -> assertThat(r.isSuccess())
                        .as("every operation on a solvent account must eventually succeed, retrying the same"
                                + " idempotency key on typed CONCURRENCY_CONFLICT backpressure (ADR-0054)")
                        .isTrue());

        BalanceView balance =
                getBalance.handle(new GetBalanceQuery(account.toString())).orElseThrow();
        assertThat(balance.balance()).isEqualTo(Money.of(100_000).orElseThrow());
    }
}
