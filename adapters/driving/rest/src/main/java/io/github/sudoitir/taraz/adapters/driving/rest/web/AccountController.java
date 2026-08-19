package io.github.sudoitir.taraz.adapters.driving.rest.web;

import io.github.sudoitir.taraz.adapters.driving.rest.dto.AccountResponse;
import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapper;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Account lifecycle and balance read (ADR-0008/0043). Pure driving adapter: inbound ports only. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account lifecycle and balance read")
public final class AccountController {

    private final CreateAccountUseCase createAccount;
    private final GetBalanceUseCase getBalance;
    private final RestMapper mapper;
    private final ProblemFactory problems;

    /** Server-assigned id (UUIDv7), zero balance. No {@code Idempotency-Key} — nothing to deduplicate. */
    @Operation(summary = "Create an account with zero balance and a server-assigned UUIDv7 id")
    @ApiResponse(responseCode = "201", description = "Account created")
    @PostMapping("/accounts")
    public ResponseEntity<?> open() {
        return switch (createAccount.handle()) {
            case Result.Success<BalanceView>(var view) -> {
                AccountResponse body = mapper.toResponse(view);
                yield ResponseEntity.created(URI.create("/accounts/" + body.accountId()))
                        .body(body);
            }
            case Result.Failure<BalanceView>(var error) -> problems.toResponse(error);
        };
    }

    /** Financial read: never cacheable (ADR-0043). */
    @Operation(summary = "Get the current balance of an account (never cached)")
    @ApiResponse(responseCode = "200", description = "Current balance")
    @ApiResponse(
            responseCode = "404",
            description = "Unknown account",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<?> balance(@PathVariable String accountId) {
        return switch (getBalance.handle(new GetBalanceQuery(accountId))) {
            case Result.Success<BalanceView>(var view) ->
                ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapper.toResponse(view));
            case Result.Failure<BalanceView>(var error) -> problems.toResponse(error);
        };
    }
}
