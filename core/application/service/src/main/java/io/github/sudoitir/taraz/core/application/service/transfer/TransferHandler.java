package io.github.sudoitir.taraz.core.application.service.transfer;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
import io.github.sudoitir.taraz.core.application.ports.outbound.AccountRepository;
import io.github.sudoitir.taraz.core.application.ports.outbound.GateDecision;
import io.github.sudoitir.taraz.core.application.ports.outbound.IdempotencyGate;
import io.github.sudoitir.taraz.core.application.ports.outbound.OutboxAppender;
import io.github.sudoitir.taraz.core.application.ports.outbound.ProcessedTransactionStore;
import io.github.sudoitir.taraz.core.application.ports.outbound.TransactionRepository;
import io.github.sudoitir.taraz.core.application.ports.outbound.UnitOfWork;
import io.github.sudoitir.taraz.core.application.service.support.CommandOutcomes;
import io.github.sudoitir.taraz.core.application.service.support.CommandValidator;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.service.PostingService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The transfer use case's command handler. {@link #applyWithinTransaction} locks both account rows with
 * one call to {@link AccountRepository#lockAllInIdOrder} — the port, not this handler, decides the
 * acquisition order (ADR-0026/0042), so no direction-dependent ordering can leak in here.
 */
@Service
public final class TransferHandler implements TransferUseCase {

    private final PostingService postingService;
    private final Clock clock;
    private final CommandValidator validator;
    private final UnitOfWork unitOfWork;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final ProcessedTransactionStore processed;
    private final OutboxAppender outbox;
    private final IdempotencyGate gate;

    public TransferHandler(
            PostingService postingService,
            Clock clock,
            CommandValidator validator,
            UnitOfWork unitOfWork,
            AccountRepository accounts,
            TransactionRepository transactions,
            ProcessedTransactionStore processed,
            OutboxAppender outbox,
            IdempotencyGate gate) {
        this.postingService = Objects.requireNonNull(postingService, "postingService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.processed = Objects.requireNonNull(processed, "processed");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public Result<CommandOutcome> handle(TransferCommand command) {
        // TransferIntent.from rejects a same-account transfer here — before the gate is ever consulted,
        // so the transactionId is not spent on a rejected request (ADR-0028).
        return TransferIntent.from(command, validator).flatMap(this::withGate);
    }

    private Result<CommandOutcome> withGate(TransferIntent intent) {
        return switch (gate.tryBegin(intent.transactionId())) {
            case GateDecision.AlreadyApplied(var outcome) -> Result.success(CommandOutcomes.asReplay(outcome));
            case GateDecision.Won ignored -> apply(intent);
            case GateDecision.Unknown ignored -> apply(intent);
        };
    }

    private Result<CommandOutcome> apply(TransferIntent intent) {
        Result<CommandOutcome> outcome = unitOfWork.inTransaction(() -> applyWithinTransaction(intent));
        if (outcome.isSuccess()) {
            gate.publishOutcome(intent.transactionId(), outcome.orElseThrow());
        } else {
            gate.release(intent.transactionId());
        }
        return outcome;
    }

    private Result<CommandOutcome> applyWithinTransaction(TransferIntent intent) {
        return accounts.lockAllInIdOrder(intent.accountIds()).flatMap(locked -> replayOrPost(intent, locked));
    }

    private Result<CommandOutcome> replayOrPost(TransferIntent intent, List<Account> locked) {
        return processed
                .find(intent.transactionId())
                .map(CommandOutcomes::asReplay)
                .<Result<CommandOutcome>>map(Result::success)
                .orElseGet(() -> post(intent, locked));
    }

    private Result<CommandOutcome> post(TransferIntent intent, List<Account> locked) {
        Account source = findAccount(locked, intent.source());
        Account destination = findAccount(locked, intent.destination());
        Instant at = clock.instant();
        return postingService
                .transfer(source, destination, intent.amount(), intent.transactionId(), at)
                .map(result -> {
                    accounts.saveAll(result.mutatedAccounts());
                    transactions.save(result.transaction());
                    outbox.append(CommandOutcomes.pullEvents(result));
                    CommandOutcome outcome = CommandOutcomes.outcomeOf(result, OutcomeStatus.APPLIED);
                    processed.record(result.transaction().id(), outcome);
                    return outcome;
                });
    }

    private static Account findAccount(List<Account> locked, AccountId id) {
        return locked.stream()
                .filter(account -> account.id().equals(id))
                .findFirst()
                // Programmer error: lockAllInIdOrder is contracted to return exactly the requested ids.
                .orElseThrow(() -> new IllegalStateException("missing account " + id));
    }
}
