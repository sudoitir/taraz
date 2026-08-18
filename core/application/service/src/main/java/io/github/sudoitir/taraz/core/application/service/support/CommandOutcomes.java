package io.github.sudoitir.taraz.core.application.service.support;

import io.github.sudoitir.taraz.core.application.ports.AccountBalance;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.domain.account.Account;
import io.github.sudoitir.taraz.core.domain.common.DomainEvent;
import io.github.sudoitir.taraz.core.domain.service.PostingResult;
import java.util.ArrayList;
import java.util.List;

/**
 * The mechanical last step every write-side handler shares once {@code PostingService} has produced a
 * result: collect every recorded domain event exactly once (ADR-0009/0010), and build the
 * {@link CommandOutcome} that both the caller and {@code ProcessedTransactionStore} receive. Pulled out
 * because it is byte-identical across credit/debit/transfer — three copies of this would be a real
 * correctness risk (a fix applied to one and forgotten in the others), not a stylistic one.
 */
public final class CommandOutcomes {

    private CommandOutcomes() {}

    /** Pulls every event recorded on the mutated accounts and the transaction itself — exactly once. */
    public static List<DomainEvent> pullEvents(PostingResult result) {
        List<DomainEvent> events = new ArrayList<>();
        for (Account account : result.mutatedAccounts()) {
            events.addAll(account.pullDomainEvents());
        }
        events.addAll(result.transaction().pullDomainEvents());
        return events;
    }

    public static CommandOutcome outcomeOf(PostingResult result, OutcomeStatus status) {
        List<AccountBalance> balances = result.mutatedAccounts().stream()
                .map(account -> new AccountBalance(account.id(), account.balance()))
                .toList();
        return new CommandOutcome(result.transaction().id(), status, balances);
    }

    /**
     * A stored outcome always carries {@code APPLIED} — that is what actually happened when it was
     * recorded. Each individual call decides for itself whether it is reporting that original
     * application or a replay of it; this re-tags a found record as {@code REPLAYED} for the caller
     * that found it, without touching what is persisted.
     */
    public static CommandOutcome asReplay(CommandOutcome stored) {
        return new CommandOutcome(stored.transactionId(), OutcomeStatus.REPLAYED, stored.balances());
    }
}
