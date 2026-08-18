package io.github.sudoitir.taraz.adapters.driving.rest.mapper;

import io.github.sudoitir.taraz.adapters.driving.rest.dto.AccountResponse;
import io.github.sudoitir.taraz.adapters.driving.rest.dto.BalanceEntry;
import io.github.sudoitir.taraz.adapters.driving.rest.dto.CommandOutcomeResponse;
import io.github.sudoitir.taraz.adapters.driving.rest.dto.CreditRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.dto.DebitRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.dto.TransferRequest;
import io.github.sudoitir.taraz.core.application.ports.AccountBalance;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * All REST-side translation in one place (ADR-0031). Touches only the domain value objects the port
 * contracts expose ({@code Money}, {@code AccountId}, {@code TransactionId}) — via their accessors,
 * flattening them to {@code BigDecimal}/{@code String} on the wire.
 */
@Mapper(componentModel = "spring")
public interface RestMapper {

    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "amount", source = "body.amount")
    @Mapping(target = "transactionId", source = "transactionId")
    CreditCommand toCommand(String accountId, CreditRequest body, String transactionId);

    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "amount", source = "body.amount")
    @Mapping(target = "transactionId", source = "transactionId")
    DebitCommand toCommand(String accountId, DebitRequest body, String transactionId);

    @Mapping(target = "transactionId", source = "transactionId")
    TransferCommand toCommand(TransferRequest body, String transactionId);

    @Mapping(target = "accountId", source = "accountId.value")
    @Mapping(target = "balance", source = "balance.minorUnits")
    AccountResponse toResponse(BalanceView view);

    @Mapping(target = "transactionId", source = "transactionId.value")
    CommandOutcomeResponse toResponse(CommandOutcome outcome);

    @Mapping(target = "accountId", source = "accountId.value")
    @Mapping(target = "balance", source = "balance.minorUnits")
    BalanceEntry toEntry(AccountBalance balance);
}
