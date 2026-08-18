package io.github.sudoitir.taraz.adapters.driving.rest.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapperImpl;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.AccountBalance;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import io.github.sudoitir.taraz.core.domain.transaction.TransactionId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TransferController.class,
        properties = {"spring.jackson.property-naming-strategy=SNAKE_CASE", "spring.mvc.problemdetails.enabled=true"})
@Import({ProblemFactory.class, RestMapperImpl.class})
class TransferControllerTest {

    private static final String SOURCE_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String DESTINATION_ID = "123e4567-e89b-42d3-a456-426614174001";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransferUseCase transferUseCase;

    @Test
    void transferAppliesAtomicallyAndReturns201ForTheSource() throws Exception {
        given(transferUseCase.handle(any(TransferCommand.class)))
                .willReturn(Result.success(outcome(OutcomeStatus.APPLIED)));

        mvc.perform(post("/transfers")
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SOURCE_ID, DESTINATION_ID, 300)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/" + SOURCE_ID + "/balance"))
                .andExpect(jsonPath("$.transaction_id").value("TX-9"))
                .andExpect(jsonPath("$.balances[0].account_id").value(SOURCE_ID))
                .andExpect(jsonPath("$.balances[0].balance").value(700))
                .andExpect(jsonPath("$.balances[1].account_id").value(DESTINATION_ID))
                .andExpect(jsonPath("$.balances[1].balance").value(800));
    }

    @Test
    void snakeCaseRequestBodyMapsToTheTransferCommand() throws Exception {
        given(transferUseCase.handle(any(TransferCommand.class)))
                .willReturn(Result.success(outcome(OutcomeStatus.APPLIED)));

        mvc.perform(post("/transfers")
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SOURCE_ID, DESTINATION_ID, 300)))
                .andExpect(status().isCreated());

        verify(transferUseCase)
                .handle(argThat((TransferCommand c) -> c.sourceAccountId().equals(SOURCE_ID)
                        && c.destinationAccountId().equals(DESTINATION_ID)
                        && c.amount() == 300
                        && c.transactionId().equals("TX-9")));
    }

    @Test
    void replayedTransferKeeps201AndFlagsIdempotencyReplayed() throws Exception {
        given(transferUseCase.handle(any(TransferCommand.class)))
                .willReturn(Result.success(outcome(OutcomeStatus.REPLAYED)));

        mvc.perform(post("/transfers")
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SOURCE_ID, DESTINATION_ID, 300)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/" + SOURCE_ID + "/balance"))
                .andExpect(header().string(RestHeaders.IDEMPOTENCY_REPLAYED, "true"));
    }

    @Test
    void sameAccountTransferIsAProblemDetail422() throws Exception {
        given(transferUseCase.handle(any(TransferCommand.class)))
                .willReturn(Result.failure(new DomainError(ErrorCode.SAME_ACCOUNT_TRANSFER, "source == destination")));

        mvc.perform(post("/transfers")
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SOURCE_ID, SOURCE_ID, 300)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));
    }

    @Test
    void missingIdempotencyKeyIsAProblemDetail400() throws Exception {
        mvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SOURCE_ID, DESTINATION_ID, 300)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_ID"));
    }

    @Test
    void blankSourceAccountIdIsAProblemDetail400() throws Exception {
        mvc.perform(post("/transfers")
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", DESTINATION_ID, 300)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_ID"));
    }

    private static String body(String source, String destination, long amount) {
        return """
                {"source_account_id": "%s", "destination_account_id": "%s", "amount": %d}""".formatted(source, destination, amount);
    }

    private static CommandOutcome outcome(OutcomeStatus status) {
        return new CommandOutcome(
                new TransactionId("TX-9"),
                status,
                List.of(
                        new AccountBalance(AccountId.of(SOURCE_ID).orElseThrow(), new Money(BigDecimal.valueOf(700))),
                        new AccountBalance(
                                AccountId.of(DESTINATION_ID).orElseThrow(), new Money(BigDecimal.valueOf(800)))));
    }
}
