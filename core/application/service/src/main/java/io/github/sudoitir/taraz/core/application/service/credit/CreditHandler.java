package io.github.sudoitir.taraz.core.application.service.credit;

import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
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
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.service.PostingService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * The credit use case's command handler. The atomic unit — lock, replay check, post, persist, publish
 * events — is exactly the body of {@link #applyWithinTransaction}, wrapped by the single
 * {@link UnitOfWork#inTransaction} call in {@link #apply} (ADR-0018/0040): the transaction boundary is
 * that one line, not an annotation.
 */
@Service
public final class CreditHandler implements CreditUseCase {

    private final PostingService postingService;
    private final Clock clock;
    private final CommandValidator validator;
    private final UnitOfWork unitOfWork;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final ProcessedTransactionStore processed;
    private final OutboxAppender outbox;
    private final IdempotencyGate gate;

    public CreditHandler(
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
    public Result<CommandOutcome> handle(CreditCommand command) {
        return CreditIntent.from(command, validator).flatMap(this::withGate);
    }

    private Result<CommandOutcome> withGate(CreditIntent intent) {
        return switch (gate.tryBegin(intent.transactionId())) {
            case GateDecision.AlreadyApplied(var outcome) -> Result.success(CommandOutcomes.asReplay(outcome));
            case GateDecision.Won ignored -> apply(intent);
            case GateDecision.Unknown ignored -> apply(intent);
        };
    }

    private Result<CommandOutcome> apply(CreditIntent intent) {
        Result<CommandOutcome> outcome = unitOfWork.inTransaction(() -> applyWithinTransaction(intent));
        if (outcome.isSuccess()) {
            gate.publishOutcome(intent.transactionId(), outcome.orElseThrow());
        } else {
            gate.release(intent.transactionId());
        }
        return outcome;
    }

    private Result<CommandOutcome> applyWithinTransaction(CreditIntent intent) {
        return accounts.lockAllInIdOrder(List.of(intent.accountId())).flatMap(locked -> replayOrPost(intent, locked));
    }

    private Result<CommandOutcome> replayOrPost(CreditIntent intent, List<Account> locked) {
        return processed
                .find(intent.transactionId())
                .map(CommandOutcomes::asReplay)
                .<Result<CommandOutcome>>map(Result::success)
                .orElseGet(() -> post(intent, locked.get(0)));
    }

    private Result<CommandOutcome> post(CreditIntent intent, Account account) {
        Instant at = clock.instant();
        return postingService
                .credit(account, intent.amount(), intent.transactionId(), at)
                .map(result -> {
                    accounts.saveAll(result.mutatedAccounts());
                    transactions.save(result.transaction());
                    outbox.append(CommandOutcomes.pullEvents(result));
                    CommandOutcome outcome = CommandOutcomes.outcomeOf(result, OutcomeStatus.APPLIED);
                    processed.record(result.transaction().id(), outcome);
                    return outcome;
                });
    }
}
