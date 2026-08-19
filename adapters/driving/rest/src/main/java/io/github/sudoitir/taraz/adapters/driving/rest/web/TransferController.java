package io.github.sudoitir.taraz.adapters.driving.rest.web;

import io.github.sudoitir.taraz.adapters.driving.rest.dto.TransferRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapper;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Two-account atomic transfer (ADR-0008/0026/0043). {@code Idempotency-Key} IS the transaction id. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Atomic transfer between two accounts")
public final class TransferController {

    private final TransferUseCase transferUseCase;
    private final RestMapper mapper;
    private final ProblemFactory problems;

    @Operation(
            summary =
                    "Transfer an amount atomically between two accounts, exactly once per Idempotency-Key; same-account transfer is rejected")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transfer applied (or replayed with the stored outcome)"),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid amount / account id, same-account transfer, or missing Idempotency-Key",
                content =
                        @Content(
                                mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(
                responseCode = "404",
                description = "Unknown source or destination account",
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
    @PostMapping("/transfers")
    public ResponseEntity<?> transfer(
            @Parameter(
                            description =
                                    "Unique id of this financial operation; a replayed key returns the stored outcome instead of re-applying",
                            required = true)
                    @RequestHeader(RestHeaders.IDEMPOTENCY_KEY)
                    String idempotencyKey,
            @Valid @RequestBody TransferRequest body) {
        return switch (transferUseCase.handle(mapper.toCommand(body, idempotencyKey))) {
            case Result.Success<CommandOutcome>(var outcome) ->
                CommandResponses.created(outcome, mapper, body.sourceAccountId());
            case Result.Failure<CommandOutcome>(var error) -> problems.toResponse(error);
        };
    }
}
