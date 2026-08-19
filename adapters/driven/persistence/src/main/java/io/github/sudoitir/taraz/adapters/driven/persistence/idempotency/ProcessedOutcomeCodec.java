package io.github.sudoitir.taraz.adapters.driven.persistence.idempotency;

import io.github.sudoitir.taraz.core.application.ports.AccountBalance;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes a {@link CommandOutcome} for durable storage — the {@code processed_transaction.outcome}
 * jsonb column (ADR-0021/0041) and, identically, the Valkey advisory cache value (ADR-0020/0021). One
 * shared shape so both stores agree on what a "completed outcome" looks like.
 *
 * <p>Amounts are always decimal <em>strings</em>, never JSON numbers: {@code Money} is an exact,
 * unbounded {@code BigDecimal} (ADR-0036), and a JSON number can silently lose precision by round-
 * tripping through a parser's double. {@code status} is deliberately not stored — a snapshot is always
 * the result of an {@code APPLIED} application by construction; the caller that finds one re-tags it
 * {@code REPLAYED} (see {@code CommandOutcomes.asReplay} in the application layer).
 *
 * <p>Deliberately uses its own {@link ObjectMapper}, not the web layer's: a REST-side Jackson
 * configuration change (e.g. snake_case naming) must never silently change what these adapters read or
 * write.
 */
@Component
public final class ProcessedOutcomeCodec {

    private static final int VERSION = 1;

    private final ObjectMapper mapper = new ObjectMapper();

    public String toJson(CommandOutcome outcome) {
        List<BalanceSnapshot> balances = outcome.balances().stream()
                .map(b -> new BalanceSnapshot(
                        b.accountId().value().toString(),
                        b.balance().minorUnits().toPlainString()))
                .toList();
        return mapper.writeValueAsString(new OutcomeSnapshot(VERSION, balances));
    }

    /**
     * Reconstructs the outcome for a caller that found an existing record — always tagged
     * {@link OutcomeStatus#APPLIED}: the record's own existence means an application already
     * happened. Throws if the payload's schema version does not match, so a future incompatible
     * change fails loudly as "unreadable", never silently as a wrong value.
     */
    public CommandOutcome fromJson(TransactionId id, String json) {
        OutcomeSnapshot snapshot = mapper.readValue(json, OutcomeSnapshot.class);
        if (snapshot.v() != VERSION) {
            throw new IllegalStateException("unsupported outcome snapshot version: " + snapshot.v());
        }
        List<AccountBalance> balances = snapshot.balances().stream()
                .map(b -> new AccountBalance(
                        new AccountId(UUID.fromString(b.accountId())), new Money(new BigDecimal(b.balance()))))
                .toList();
        return new CommandOutcome(id, OutcomeStatus.APPLIED, balances);
    }

    private record BalanceSnapshot(String accountId, String balance) {}

    private record OutcomeSnapshot(int v, List<BalanceSnapshot> balances) {}
}
