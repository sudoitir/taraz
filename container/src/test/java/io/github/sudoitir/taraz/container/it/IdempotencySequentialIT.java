package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * `.claude/rules/challenge-idempotency.md` reference scenario, against the real Valkey gate + Postgres
 * {@code processed_transaction} guard: the same {@code transactionId} sent three times in sequence must
 * change the balance exactly once, for credit, debit, and transfer alike.
 */
@TarazIntegrationTest
class IdempotencySequentialIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private DebitUseCase debit;

    @Autowired
    private TransferUseCase transfer;

    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void tripleCreditWithSameTransactionIdChangesBalanceExactlyOnce() {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        String txId = "TX-CREDIT-" + UUID.randomUUID();

        Result<CommandOutcome> first = credit.handle(new CreditCommand(account.toString(), 500, txId));
        Result<CommandOutcome> second = credit.handle(new CreditCommand(account.toString(), 500, txId));
        Result<CommandOutcome> third = credit.handle(new CreditCommand(account.toString(), 500, txId));

        assertThat(first.orElseThrow().status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(second.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);
        assertThat(third.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);

        BalanceView balance =
                getBalance.handle(new GetBalanceQuery(account.toString())).orElseThrow();
        assertThat(balance.balance()).isEqualTo(Money.of(500).orElseThrow());
    }

    @Test
    void tripleDebitWithSameTransactionIdChangesBalanceExactlyOnce() {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        credit.handle(new CreditCommand(account.toString(), 1000, "SEED-" + UUID.randomUUID()))
                .orElseThrow();
        String txId = "TX-DEBIT-" + UUID.randomUUID();

        Result<CommandOutcome> first = debit.handle(new DebitCommand(account.toString(), 100, txId));
        Result<CommandOutcome> second = debit.handle(new DebitCommand(account.toString(), 100, txId));
        Result<CommandOutcome> third = debit.handle(new DebitCommand(account.toString(), 100, txId));

        assertThat(first.orElseThrow().status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(second.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);
        assertThat(third.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);

        BalanceView balance =
                getBalance.handle(new GetBalanceQuery(account.toString())).orElseThrow();
        assertThat(balance.balance()).isEqualTo(Money.of(900).orElseThrow());
    }

    @Test
    void tripleTransferWithSameTransactionIdMovesFundsExactlyOnce() {
        AccountId source = createAccount.handle().orElseThrow().accountId();
        AccountId destination = createAccount.handle().orElseThrow().accountId();
        credit.handle(new CreditCommand(source.toString(), 1000, "SEED-" + UUID.randomUUID()))
                .orElseThrow();
        String txId = "TX-XFER-" + UUID.randomUUID();

        Result<CommandOutcome> first =
                transfer.handle(new TransferCommand(source.toString(), destination.toString(), 300, txId));
        Result<CommandOutcome> second =
                transfer.handle(new TransferCommand(source.toString(), destination.toString(), 300, txId));
        Result<CommandOutcome> third =
                transfer.handle(new TransferCommand(source.toString(), destination.toString(), 300, txId));

        assertThat(first.orElseThrow().status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(second.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);
        assertThat(third.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);

        BalanceView sourceBalance =
                getBalance.handle(new GetBalanceQuery(source.toString())).orElseThrow();
        BalanceView destinationBalance =
                getBalance.handle(new GetBalanceQuery(destination.toString())).orElseThrow();
        assertThat(sourceBalance.balance()).isEqualTo(Money.of(700).orElseThrow());
        assertThat(destinationBalance.balance()).isEqualTo(Money.of(300).orElseThrow());
    }
}
