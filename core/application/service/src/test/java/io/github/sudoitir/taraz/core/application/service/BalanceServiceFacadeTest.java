package io.github.sudoitir.taraz.core.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceOperationException;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.application.service.credit.CreditHandler;
import io.github.sudoitir.taraz.core.application.service.debit.DebitHandler;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeAccountRepository;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeIdempotencyGate;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeOutboxAppender;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeProcessedTransactionStore;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeTransactionRepository;
import io.github.sudoitir.taraz.core.application.service.fakes.FakeUnitOfWork;
import io.github.sudoitir.taraz.core.application.service.support.TestValidator;
import io.github.sudoitir.taraz.core.application.service.transfer.TransferHandler;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.IdGenerator;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.common.UuidV7IdGenerator;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.service.PostingService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BalanceServiceFacadeTest {

    private static final Instant AT = Instant.parse("2026-08-18T10:15:30Z");
    private static final IdGenerator IDS = new UuidV7IdGenerator();

    private FakeAccountRepository accounts;
    private StubGetBalanceUseCase getBalance;
    private BalanceServiceFacade facade;

    @BeforeEach
    void setUp() {
        accounts = new FakeAccountRepository();
        FakeUnitOfWork unitOfWork = new FakeUnitOfWork(accounts);
        FakeTransactionRepository transactions = new FakeTransactionRepository();
        FakeProcessedTransactionStore processed = new FakeProcessedTransactionStore();
        FakeOutboxAppender outbox = new FakeOutboxAppender();
        var validator = TestValidator.commandValidator();
        var clock = Clock.fixed(AT, ZoneOffset.UTC);
        var postingService = new PostingService(IDS);

        CreditHandler credit = new CreditHandler(
                postingService,
                clock,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL));
        DebitHandler debit = new DebitHandler(
                postingService,
                clock,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL));
        TransferHandler transfer = new TransferHandler(
                postingService,
                clock,
                validator,
                unitOfWork,
                accounts,
                transactions,
                processed,
                outbox,
                new FakeIdempotencyGate(FakeIdempotencyGate.Mode.NORMAL));
        getBalance = new StubGetBalanceUseCase();

        facade = new BalanceServiceFacade(credit, debit, transfer, getBalance);
    }

    private static AccountId newAccountId() {
        return new AccountId(IDS.newId());
    }

    @Test
    void creditDelegatesToCreditUseCase() {
        AccountId id = newAccountId();
        accounts.seed(id, Money.of(1000).orElseThrow());

        facade.credit(id.toString(), 500, "TX-1");

        assertThat(accounts.balanceOf(id)).isEqualTo(Money.of(1500).orElseThrow());
    }

    @Test
    void debitFailureSurfacesAsBalanceOperationException() {
        AccountId id = newAccountId();
        accounts.seed(id, Money.of(100).orElseThrow());

        assertThatThrownBy(() -> facade.debit(id.toString(), 700, "TX-2"))
                .isInstanceOf(BalanceOperationException.class)
                .satisfies(
                        e -> assertThat(((BalanceOperationException) e).error().code())
                                .isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
        assertThat(accounts.balanceOf(id)).isEqualTo(Money.of(100).orElseThrow());
    }

    @Test
    void transferDelegatesToTransferUseCase() {
        AccountId a = newAccountId();
        AccountId b = newAccountId();
        accounts.seed(a, Money.of(1000).orElseThrow());
        accounts.seed(b, Money.of(500).orElseThrow());

        facade.transfer(a.toString(), b.toString(), 300, "TX-3");

        assertThat(accounts.balanceOf(a)).isEqualTo(Money.of(700).orElseThrow());
        assertThat(accounts.balanceOf(b)).isEqualTo(Money.of(800).orElseThrow());
    }

    @Test
    void getBalanceReturnsExactLongForInRangeBalances() {
        AccountId id = newAccountId();
        getBalance.seed(id, 42_000L);

        assertThat(facade.getBalance(id.toString())).isEqualTo(42_000L);
    }

    @Test
    void getBalanceThrowsOnOverflowInsteadOfTruncating() {
        AccountId id = newAccountId();
        BigDecimal beyondLongRange = BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.ONE);
        getBalance.seedExact(id, beyondLongRange);

        assertThatThrownBy(() -> facade.getBalance(id.toString())).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void getBalanceOnUnknownAccountSurfacesAsBalanceOperationException() {
        assertThatThrownBy(() -> facade.getBalance(newAccountId().toString()))
                .isInstanceOf(BalanceOperationException.class)
                .satisfies(
                        e -> assertThat(((BalanceOperationException) e).error().code())
                                .isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /** A minimal read-side stub — the real {@code GetBalanceHandler} lives in the query module. */
    private static final class StubGetBalanceUseCase implements GetBalanceUseCase {
        private final Map<AccountId, Money> balances = new HashMap<>();

        void seed(AccountId id, long amount) {
            balances.put(id, Money.of(amount).orElseThrow());
        }

        void seedExact(AccountId id, BigDecimal amount) {
            balances.put(id, Money.of(amount).orElseThrow());
        }

        @Override
        public Result<BalanceView> handle(GetBalanceQuery query) {
            AccountId id = AccountId.of(query.accountId()).orElseThrow();
            Money balance = balances.get(id);
            if (balance == null) {
                return Result.failure(ErrorCode.ACCOUNT_NOT_FOUND, "unknown account: " + id);
            }
            return Result.success(new BalanceView(id, balance));
        }
    }
}
