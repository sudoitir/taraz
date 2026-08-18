package io.github.sudoitir.taraz.core.application.service.account;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeAccountRepository;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeOutboxAppender;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeUnitOfWork;
import io.github.sudoitir.taraz.core.domain.account.event.AccountOpened;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateAccountHandlerTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");

    private final IdGenerator ids = new UuidV7IdGenerator();
    private FakeAccountRepository accounts;
    private FakeOutboxAppender outbox;
    private CreateAccountHandler handler;

    @BeforeEach
    void setUp() {
        accounts = new FakeAccountRepository();
        outbox = new FakeOutboxAppender();
        handler = new CreateAccountHandler(
                ids, Clock.fixed(AT, ZoneOffset.UTC), new FakeUnitOfWork(accounts), accounts, outbox);
    }

    @Test
    void opensAccountWithZeroBalance() {
        BalanceView view = handler.handle().orElseThrow();

        assertThat(view.balance()).isEqualTo(Money.ZERO);
        assertThat(accounts.balanceOf(view.accountId())).isEqualTo(Money.ZERO);
    }

    @Test
    void appendsAccountOpenedToTheOutbox() {
        BalanceView view = handler.handle().orElseThrow();

        assertThat(outbox.events()).singleElement().isInstanceOf(AccountOpened.class);
    }

    @Test
    void repeatedInvocationsCreateDistinctAccounts() {
        BalanceView first = handler.handle().orElseThrow();
        BalanceView second = handler.handle().orElseThrow();

        assertThat(first.accountId()).isNotEqualTo(second.accountId());
        assertThat(accounts.balanceOf(first.accountId())).isEqualTo(Money.ZERO);
        assertThat(accounts.balanceOf(second.accountId())).isEqualTo(Money.ZERO);
    }
}
