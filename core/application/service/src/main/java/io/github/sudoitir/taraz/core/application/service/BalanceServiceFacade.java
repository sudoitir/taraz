package io.github.sudoitir.taraz.core.application.service;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceOperationException;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceService;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The challenge's mandated facade (design.md D3): delegates to the real CQRS ports and is the one place
 * a {@link Result} failure becomes a thrown {@link BalanceOperationException}. Not used by driving
 * adapters for reads — {@link GetBalanceUseCase} is called directly for that (ADR-0033).
 */
@Service
public final class BalanceServiceFacade implements BalanceService {

    private final CreditUseCase credit;
    private final DebitUseCase debit;
    private final TransferUseCase transfer;
    private final GetBalanceUseCase getBalance;

    public BalanceServiceFacade(
            CreditUseCase credit, DebitUseCase debit, TransferUseCase transfer, GetBalanceUseCase getBalance) {
        this.credit = Objects.requireNonNull(credit, "credit");
        this.debit = Objects.requireNonNull(debit, "debit");
        this.transfer = Objects.requireNonNull(transfer, "transfer");
        this.getBalance = Objects.requireNonNull(getBalance, "getBalance");
    }

    @Override
    public void credit(String accountId, long amount, String transactionId) {
        unwrap(credit.handle(new CreditCommand(accountId, amount, transactionId)));
    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {
        unwrap(debit.handle(new DebitCommand(accountId, amount, transactionId)));
    }

    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {
        unwrap(transfer.handle(new TransferCommand(sourceAccountId, destinationAccountId, amount, transactionId)));
    }

    /**
     * Narrows the domain's exact, unbounded {@code Money} to the challenge's mandated {@code long}. Uses
     * {@code longValueExact()} so an out-of-range balance throws {@link ArithmeticException} rather than
     * silently truncating or wrapping — the domain spec guarantees balances beyond {@code Long.MAX_VALUE}
     * are representable, so this boundary must fail loudly, not lie.
     */
    @Override
    public long getBalance(String accountId) {
        BalanceView view = unwrap(getBalance.handle(new GetBalanceQuery(accountId)));
        return view.balance().minorUnits().longValueExact();
    }

    private static <T> T unwrap(Result<T> result) {
        if (result.isFailure()) {
            DomainError error = result.error().orElseThrow();
            throw new BalanceOperationException(error);
        }
        return result.orElseThrow();
    }
}
