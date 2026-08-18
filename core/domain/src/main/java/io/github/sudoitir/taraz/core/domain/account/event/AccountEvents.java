package io.github.sudoitir.taraz.core.domain.account.event;

import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.time.Instant;

/**
 * The only route to account events (ADR-0005). Event builders are package-private so construction cannot
 * bypass this factory.
 */
public final class AccountEvents {

    private AccountEvents() {}

    public static AccountOpened opened(AccountId accountId, Money balance, Instant at) {
        return AccountOpened.builder()
                .eventType(AccountOpened.EVENT_TYPE)
                .occurredAt(at)
                .accountId(accountId)
                .balance(balance)
                .build();
    }

    public static AccountCredited credited(
            AccountId accountId, Money amount, Money balanceAfter, String transactionId, Instant at) {
        return AccountCredited.builder()
                .eventType(AccountCredited.EVENT_TYPE)
                .occurredAt(at)
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .build();
    }

    public static AccountDebited debited(
            AccountId accountId, Money amount, Money balanceAfter, String transactionId, Instant at) {
        return AccountDebited.builder()
                .eventType(AccountDebited.EVENT_TYPE)
                .occurredAt(at)
                .transactionId(transactionId)
                .accountId(accountId)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .build();
    }
}
