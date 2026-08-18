package io.github.sudoitir.taraz.core.application.service.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeAccountRepository;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeIdempotencyGate;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeOutboxAppender;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeProcessedTransactionStore;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeTransactionRepository;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeUnitOfWork;
import io.github.sudoitir.taraz.core.application.service.support.TestValidator;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.service.PostingService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferHandlerTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final IdGenerator IDS = new UuidV7IdGenerator();

    private FakeAccountRepository accounts;
    private FakeTransactionRepository transactions;
    private FakeProcessedTransactionStore processed;
    private FakeOutboxAppender outbox;
    private FakeIdempotencyGate gate;
    private TransferHandler handler;

    @BeforeEach
    void setUp() {
        accounts = new FakeAccountRepository();
        transactions = new FakeTransactionRepository();
        processed = new FakeProcessedTransactionStore();
        outbox = new FakeOutboxAppender();
        gate = new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL);
        handler = new TransferHandler(
                new PostingService(IDS),
                Clock.fixed(AT, ZoneOffset.UTC),
                TestValidator.commandValidator(),
                new FakeUnitOfWork(accounts),
                accounts,
                transactions,
                processed,
                outbox,
                gate);
    }

    private static AccountId newAccountId() {
        return new AccountId(IDS.newId());
    }

    @Test
    void transfersExactlyTheSameAmountFromSourceToDestination() {
        AccountId a = newAccountId();
        AccountId b = newAccountId();
        accounts.seed(a, Money.of(1000).orElseThrow());
        accounts.seed(b, Money.of(500).orElseThrow());

        var result = handler.handle(new TransferCommand(a.toString(), b.toString(), 300, "TX-1"));

        assertThat(result.isSuccess()).isTrue();
        CommandOutcome outcome = result.orElseThrow();
        assertThat(outcome.status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(accounts.balanceOf(a)).isEqualTo(Money.of(700).orElseThrow());
        assertThat(accounts.balanceOf(b)).isEqualTo(Money.of(800).orElseThrow());
    }

    @Test
    void insufficientSourceFundsLeavesBothBalancesUnchanged() {
        AccountId a = newAccountId();
        AccountId b = newAccountId();
        accounts.seed(a, Money.of(500).orElseThrow());
        accounts.seed(b, Money.of(500).orElseThrow());

        var result = handler.handle(new TransferCommand(a.toString(), b.toString(), 700, "TX-2"));

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(accounts.balanceOf(a)).isEqualTo(Money.of(500).orElseThrow());
        assertThat(accounts.balanceOf(b)).isEqualTo(Money.of(500).orElseThrow());
        assertThat(transactions.savedCount()).isZero();
    }

    @Test
    void sameAccountTransferRejectedBeforeAnyLockAndTransactionIdRemainsUsable() {
        AccountId a = newAccountId();
        accounts.seed(a, Money.of(1000).orElseThrow());

        var rejected = handler.handle(new TransferCommand(a.toString(), a.toString(), 100, "TX-3"));

        assertThat(rejected.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER));
        assertThat(accounts.balanceOf(a)).isEqualTo(Money.of(1000).orElseThrow());
        // the gate was never consulted for this transactionId — a later valid use of it is evaluated fresh
        AccountId b = newAccountId();
        accounts.seed(b, Money.of(0).orElseThrow());
        var later = handler.handle(new TransferCommand(a.toString(), b.toString(), 100, "TX-3"));
        assertThat(later.isSuccess()).isTrue();
    }

    @Test
    void unknownDestinationAccountFailsWithoutMutatingSource() {
        AccountId a = newAccountId();
        accounts.seed(a, Money.of(1000).orElseThrow());

        var result =
                handler.handle(new TransferCommand(a.toString(), newAccountId().toString(), 100, "TX-4"));

        assertThat(result.error()).hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
        assertThat(accounts.balanceOf(a)).isEqualTo(Money.of(1000).orElseThrow());
    }
}
