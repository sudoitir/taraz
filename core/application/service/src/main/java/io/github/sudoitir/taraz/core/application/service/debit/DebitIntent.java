package io.github.sudoitir.taraz.core.application.service.debit;

import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.service.support.CommandValidator;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;

/** A {@link DebitCommand}'s parsed, domain-typed intent — see {@code CreditIntent} for the rationale. */
public record DebitIntent(AccountId accountId, Money amount, TransactionId transactionId) {

    public static Result<DebitIntent> from(DebitCommand command, CommandValidator validator) {
        return validator
                .validate(command)
                .flatMap(valid -> AccountId.of(valid.accountId())
                        .flatMap(accountId -> Money.operationAmount(valid.amount())
                                .flatMap(amount -> TransactionId.of(valid.transactionId())
                                        .map(transactionId -> new DebitIntent(accountId, amount, transactionId)))));
    }
}
