package io.github.sudoitir.taraz.adapters.driving.rest.web;

import io.github.sudoitir.taraz.adapters.driving.rest.dto.CreditRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.dto.DebitRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapper;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitUseCase;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Single-account financial operations (ADR-0008/0043). {@code Idempotency-Key} IS the transaction id. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Account operations", description = "Credit and debit on a single account")
public final class AccountOperationsController {

    private static final String KEY_PARAM_DOC =
            "Unique id of this financial operation; a replayed key returns the stored outcome instead of re-applying";

    private final CreditUseCase creditUseCase;
    private final DebitUseCase debitUseCase;
    private final RestMapper mapper;
    private final ProblemFactory problems;

    @Operation(summary = "Credit (increase) the account balance exactly once per Idempotency-Key")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Command applied (or replayed with the stored outcome)"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid amount / account id, or missing Idempotency-Key",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Unknown account",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Idempotency-Key already used for a different request",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Transient concurrency conflict — retry",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/accounts/{accountId}/credits")
    public ResponseEntity<?> credit(
            @PathVariable String accountId,
            @Parameter(description = KEY_PARAM_DOC, required = true) @RequestHeader(RestHeaders.IDEMPOTENCY_KEY)
                    String idempotencyKey,
            @Valid @RequestBody CreditRequest body) {
        return switch (creditUseCase.handle(mapper.toCommand(accountId, body, idempotencyKey))) {
            case Result.Success<CommandOutcome>(var outcome) -> CommandResponses.created(outcome, mapper, accountId);
            case Result.Failure<CommandOutcome>(var error) -> problems.toResponse(error);
        };
    }

    @Operation(
            summary =
                    "Debit (decrease) the account balance exactly once per Idempotency-Key; fails without side effects on insufficient funds")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Command applied (or replayed with the stored outcome)"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid amount / account id, or missing Idempotency-Key",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Unknown account",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Idempotency-Key already used for a different request",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "503",
                description = "Transient concurrency conflict — retry",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/accounts/{accountId}/debits")
    public ResponseEntity<?> debit(
            @PathVariable String accountId,
            @Parameter(description = KEY_PARAM_DOC, required = true) @RequestHeader(RestHeaders.IDEMPOTENCY_KEY)
                    String idempotencyKey,
            @Valid @RequestBody DebitRequest body) {
        return switch (debitUseCase.handle(mapper.toCommand(accountId, body, idempotencyKey))) {
            case Result.Success<CommandOutcome>(var outcome) -> CommandResponses.created(outcome, mapper, accountId);
            case Result.Failure<CommandOutcome>(var error) -> problems.toResponse(error);
        };
    }
}
