package io.github.sudoitir.taraz.core.application.service.debit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
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

class DebitHandlerTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final IdGenerator IDS = new UuidV7IdGenerator();

    private FakeAccountRepository accounts;
    private FakeTransactionRepository transactions;
    private FakeProcessedTransactionStore processed;
    private FakeOutboxAppender outbox;
    private DebitHandler handler;

    @BeforeEach
    void setUp() {
        accounts = new FakeAccountRepository();
        transactions = new FakeTransactionRepository();
        processed = new FakeProcessedTransactionStore();
        outbox = new FakeOutboxAppender();
        handler = new DebitHandler(
                new PostingService(IDS),
                Clock.fixed(AT, ZoneOffset.UTC),
                TestValidator.commandValidator(),
                new FakeUnitOfWork(accounts),
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL));
    }

    private static AccountId newAccountId() {
        return new AccountId(IDS.newId());
    }

    @Test
    void debitsAnExistingAccountWithSufficientFunds() {
        AccountId id = newAccountId();
        accounts.seed(id, Money.of(1000).orElseThrow());

        var result = handler.handle(new DebitCommand(id.toString(), 700, "TX-1"));

        assertThat(result.isSuccess()).isTrue();
        CommandOutcome outcome = result.orElseThrow();
        assertThat(outcome.status()).isEqualTo(OutcomeStatus.APPLIED);
        assertThat(accounts.balanceOf(id)).isEqualTo(Money.of(300).orElseThrow());
    }

    @Test
    void insufficientFundsFailsAndLeavesBalanceUnchanged() {
        AccountId id = newAccountId();
        accounts.seed(id, Money.of(500).orElseThrow());

        var result = handler.handle(new DebitCommand(id.toString(), 700, "TX-2"));

        assertThat(result.error())
                .hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(accounts.balanceOf(id)).isEqualTo(Money.of(500).orElseThrow());
        assertThat(transactions.savedCount()).isZero();
        assertThat(processed.recordedCount()).isZero();
        assertThat(outbox.events()).isEmpty();
    }

    @Test
    void unknownAccountFailsWithoutMutatingAnything() {
        var result = handler.handle(new DebitCommand(newAccountId().toString(), 100, "TX-3"));

        assertThat(result.error()).hasValueSatisfying(e -> assertThat(e.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}
