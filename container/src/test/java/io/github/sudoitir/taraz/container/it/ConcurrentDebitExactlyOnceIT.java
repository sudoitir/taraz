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
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The challenge's headline scenario: balance 1000, two concurrent {@code debit(700)} — exactly one
 * succeeds (ADR-0026). Proven here against real {@code SELECT ... FOR UPDATE}, not the in-memory fake.
 */
@TarazIntegrationTest
class ConcurrentDebitExactlyOnceIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private DebitUseCase debit;

    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void exactlyOneOfTwoConcurrentDebitsBeyondHalfBalanceSucceeds() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        credit.handle(new CreditCommand(account.toString(), 1000, "SEED-" + UUID.randomUUID()))
                .orElseThrow();

        List<Result<CommandOutcome>> results = TestConcurrency.runConcurrently(
                2, i -> debit.handle(new DebitCommand(account.toString(), 700, "TX-RACE-" + i)));

        long successes = results.stream().filter(Result::isSuccess).count();
        long insufficientFunds = results.stream()
                .filter(Result::isFailure)
                .filter(r -> r.error().orElseThrow().code() == ErrorCode.INSUFFICIENT_FUNDS)
                .count();

        assertThat(successes).isEqualTo(1);
        assertThat(insufficientFunds).isEqualTo(1);

        BalanceView balance =
                getBalance.handle(new GetBalanceQuery(account.toString())).orElseThrow();
        assertThat(balance.balance()).isEqualTo(Money.of(300).orElseThrow());
    }
}
