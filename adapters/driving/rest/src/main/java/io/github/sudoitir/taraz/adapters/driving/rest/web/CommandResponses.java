package io.github.sudoitir.taraz.adapters.driving.rest.web;

import io.github.sudoitir.taraz.adapters.driving.rest.dto.CommandOutcomeResponse;
import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapper;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import java.net.URI;
import org.springframework.http.ResponseEntity;

/**
 * The one way a successful command becomes a response: 201 + {@code Location} pointing at the affected
 * account's balance sub-resource, plus {@code Idempotency-Replayed: true} when the outcome is a replay
 * (ADR-0043). Same input → same {@code Location}, so a replay is indistinguishable in address too.
 */
final class CommandResponses {

    private CommandResponses() {}

    static ResponseEntity<CommandOutcomeResponse> created(CommandOutcome outcome, RestMapper mapper, String accountId) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.created(URI.create("/accounts/" + accountId + "/balance"));
        if (outcome.status() == OutcomeStatus.REPLAYED) {
            builder.header(RestHeaders.IDEMPOTENCY_REPLAYED, "true");
        }
        return builder.body(mapper.toResponse(outcome));
    }
}
