package io.github.sudoitir.taraz.adapters.driving.rest.web;

import io.github.sudoitir.taraz.adapters.driving.rest.dto.TransferRequest;
import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapper;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
import io.github.sudoitir.taraz.core.domain.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Two-account atomic transfer (ADR-0008/0026/0043). {@code Idempotency-Key} IS the transaction id. */
@RestController
@RequiredArgsConstructor
public final class TransferController {

    private final TransferUseCase transferUseCase;
    private final RestMapper mapper;
    private final ProblemFactory problems;

    @PostMapping("/transfers")
    public ResponseEntity<?> transfer(
            @RequestHeader(RestHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody TransferRequest body) {
        return switch (transferUseCase.handle(mapper.toCommand(body, idempotencyKey))) {
            case Result.Success<CommandOutcome>(var outcome) ->
                CommandResponses.created(outcome, mapper, body.sourceAccountId());
            case Result.Failure<CommandOutcome>(var error) -> problems.toResponse(error);
        };
    }
}
