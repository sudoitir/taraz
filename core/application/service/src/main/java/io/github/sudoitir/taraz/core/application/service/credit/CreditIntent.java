package io.github.sudoitir.taraz.core.application.service.credit;

import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.service.support.CommandValidator;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;

/**
 * A {@link CreditCommand}'s parsed, domain-typed intent — the input {@link CreditHandler}'s atomic unit
 * acts on. Collapses structural validation and the three primitive-to-domain-value conversions into one
 * tested factory, so the handler's own composition stays a short railway (design.md D6).
 */
public record CreditIntent(AccountId accountId, Money amount, TransactionId transactionId) {

    public static Result<CreditIntent> from(CreditCommand command, CommandValidator validator) {
        return validator
                .validate(command)
                .flatMap(valid -> AccountId.of(valid.accountId())
                        .flatMap(accountId -> Money.operationAmount(valid.amount())
                                .flatMap(amount -> TransactionId.of(valid.transactionId())
                                        .map(transactionId -> new CreditIntent(accountId, amount, transactionId)))));
    }
}
