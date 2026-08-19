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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADR-0020/0021/0041/0054: with Valkey paused, {@link
 * io.github.sudoitir.taraz.adapters.driven.persistence.idempotency.ValkeyIdempotencyGate} degrades every
 * call to {@code Unknown} (fail-open) — correctness still comes from PostgreSQL's
 * {@code processed_transaction} guard, never from the cache. This also proves the resilience wiring
 * (Lettuce {@code REJECT_COMMANDS} + 200ms timeouts, design.md D4): a dead cache must turn into a fast
 * degrade, not a per-request stall.
 *
 * <p>Pauses (not stops) the shared static {@code VALKEY} container so later test classes in the same
 * suite still find it running — always unpaused in a {@code finally}, even on assertion failure.
 */
@TarazIntegrationTest
class IdempotencyGateDownFailOpenIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void exactlyOnceHoldsAndCallsStayFastWhileValkeyIsDown() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        String txId = "TX-GATE-DOWN-" + UUID.randomUUID();

        VALKEY.getDockerClient().pauseContainerCmd(VALKEY.getContainerId()).exec();
        try {
            Instant start = Instant.now();
            Result<CommandOutcome> first = credit.handle(new CreditCommand(account.toString(), 500, txId));
            Result<CommandOutcome> second = credit.handle(new CreditCommand(account.toString(), 500, txId));
            Result<CommandOutcome> third = credit.handle(new CreditCommand(account.toString(), 500, txId));
            Duration elapsed = Duration.between(start, Instant.now());

            assertThat(first.orElseThrow().status()).isEqualTo(OutcomeStatus.APPLIED);
            assertThat(second.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);
            assertThat(third.orElseThrow().status()).isEqualTo(OutcomeStatus.REPLAYED);

            // Three sequential calls each paying at most the 200ms Lettuce command/connect timeout
            // (design.md D4) stay well under a second; an unbounded queueing default would instead
            // stall each call for however long the reconnect takes.
            assertThat(elapsed)
                    .as("a dead Valkey must degrade fast (REJECT_COMMANDS + 200ms timeouts), never stall")
                    .isLessThan(Duration.ofSeconds(5));
        } finally {
            VALKEY.getDockerClient()
                    .unpauseContainerCmd(VALKEY.getContainerId())
                    .exec();
        }

        BalanceView balance =
                getBalance.handle(new GetBalanceQuery(account.toString())).orElseThrow();
        assertThat(balance.balance()).isEqualTo(Money.of(500).orElseThrow());
    }
}
