package io.github.sudoitir.taraz.core.application.service.transfer;

import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.service.support.CommandValidator;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import io.github.sudoitir.taraz.core.domain.transaction.TransferParties;
import io.github.sudoitir.taraz.core.domain.transaction.spec.DistinctTransferPartiesSpecification;
import java.util.List;

/**
 * A {@link TransferCommand}'s parsed, domain-typed intent. The same-account check (ADR-0028) runs here —
 * as soon as both account ids are known, before amount or transaction id are even parsed, and long
 * before any lock or transaction — using the standalone {@link DistinctTransferPartiesSpecification},
 * not the copy embedded in {@code Transaction.transfer}.
 */
public record TransferIntent(AccountId source, AccountId destination, Money amount, TransactionId transactionId) {

    private static final DistinctTransferPartiesSpecification DISTINCT_PARTIES =
            new DistinctTransferPartiesSpecification();

    public static Result<TransferIntent> from(TransferCommand command, CommandValidator validator) {
        return validator
                .validate(command)
                .flatMap(valid -> AccountId.of(valid.sourceAccountId())
                        .flatMap(source -> AccountId.of(valid.destinationAccountId())
                                .flatMap(destination -> DISTINCT_PARTIES
                                        .check(new TransferParties(source, destination))
                                        .flatMap(parties -> Money.operationAmount(valid.amount())
                                                .flatMap(amount -> TransactionId.of(valid.transactionId())
                                                        .map(transactionId -> new TransferIntent(
                                                                source, destination, amount, transactionId)))))));
    }

    public List<AccountId> accountIds() {
        return List.of(source, destination);
    }
}
