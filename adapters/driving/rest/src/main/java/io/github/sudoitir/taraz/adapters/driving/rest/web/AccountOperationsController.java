package io.github.sudoitir.taraz.adapters.driving.rest.web;

import io.github.sudoitir.taraz.adapters.driving.rest.dto.CreditRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.dto.DebitRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapper;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitUseCase;
import io.github.sudoitir.taraz.core.domain.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Single-account financial operations (ADR-0008/0043). {@code Idempotency-Key} IS the transaction id. */
@RestController
@RequiredArgsConstructor
public final class AccountOperationsController {

    private final CreditUseCase creditUseCase;
    private final DebitUseCase debitUseCase;
    private final RestMapper mapper;
    private final ProblemFactory problems;

    @PostMapping("/accounts/{accountId}/credits")
    public ResponseEntity<?> credit(
            @PathVariable String accountId,
            @RequestHeader(RestHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreditRequest body) {
        return switch (creditUseCase.handle(mapper.toCommand(accountId, body, idempotencyKey))) {
            case Result.Success<CommandOutcome>(var outcome) -> CommandResponses.created(outcome, mapper, accountId);
            case Result.Failure<CommandOutcome>(var error) -> problems.toResponse(error);
        };
    }

    @PostMapping("/accounts/{accountId}/debits")
    public ResponseEntity<?> debit(
            @PathVariable String accountId,
            @RequestHeader(RestHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody DebitRequest body) {
        return switch (debitUseCase.handle(mapper.toCommand(accountId, body, idempotencyKey))) {
            case Result.Success<CommandOutcome>(var outcome) -> CommandResponses.created(outcome, mapper, accountId);
            case Result.Failure<CommandOutcome>(var error) -> problems.toResponse(error);
        };
    }
}
