package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADR-0026/0042/0045: many concurrent transfers alternating direction between the same two accounts
 * must all complete — none aborted by a database deadlock (SQLState {@code 40P01}) — because every
 * transfer locks the two account rows in one canonical order regardless of which is source and which
 * is destination.
 */
@TarazIntegrationTest
class ConcurrentOppositeTransfersNoDeadlockIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private TransferUseCase transfer;

    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void manyConcurrentBidirectionalTransfersCompleteWithoutDeadlockAndConserveTheTotal() throws Exception {
        AccountId a = createAccount.handle().orElseThrow().accountId();
        AccountId b = createAccount.handle().orElseThrow().accountId();
        credit.handle(new CreditCommand(a.toString(), 100_000, "SEED-A-" + UUID.randomUUID()))
                .orElseThrow();
        credit.handle(new CreditCommand(b.toString(), 100_000, "SEED-B-" + UUID.randomUUID()))
                .orElseThrow();

        int perDirection = 100;
        int n = perDirection * 2;
        List<Result<CommandOutcome>> results = TestConcurrency.runConcurrentlyRetryingOnBackpressure(n, i -> {
            String txId = "TX-XFER-" + i + "-" + UUID.randomUUID();
            return () -> i % 2 == 0
                    ? transfer.handle(new TransferCommand(a.toString(), b.toString(), 1, txId))
                    : transfer.handle(new TransferCommand(b.toString(), a.toString(), 1, txId));
        });

        assertThat(results)
                .allSatisfy(r -> assertThat(r.isSuccess())
                        .as("no transfer should fail after retrying typed CONCURRENCY_CONFLICT backpressure"
                                + " (ADR-0054) with the same idempotency key — balances are ample and the lock"
                                + " order is deadlock-free by design, so no 40P01 should ever appear")
                        .isTrue());

        BalanceView balanceA =
                getBalance.handle(new GetBalanceQuery(a.toString())).orElseThrow();
        BalanceView balanceB =
                getBalance.handle(new GetBalanceQuery(b.toString())).orElseThrow();
        assertThat(balanceA.balance().plus(balanceB.balance()))
                .isEqualTo(Money.of(200_000).orElseThrow());
    }
}
