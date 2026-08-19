package io.github.sudoitir.taraz.adapters.driving.rest.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapperImpl;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.AccountBalance;
import io.github.sudoitir.taraz.core.application.ports.CommandOutcome;
import io.github.sudoitir.taraz.core.application.ports.OutcomeStatus;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitUseCase;
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
        controllers = AccountOperationsController.class,
        properties = {
            "spring.jackson.property-naming-strategy=LOWER_CAMEL_CASE",
            "spring.mvc.problemdetails.enabled=true"
        })
@Import({ProblemFactory.class, RestMapperImpl.class})
class AccountOperationsControllerTest {

    private static final String ACCOUNT_ID = "123e4567-e89b-42d3-a456-426614174000";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreditUseCase creditUseCase;

    @MockitoBean
    private DebitUseCase debitUseCase;

    @Test
    void creditAppliesAndReturns201WithLocationAndOutcomeBody() throws Exception {
        given(creditUseCase.handle(any(CreditCommand.class)))
                .willReturn(Result.success(outcome(OutcomeStatus.APPLIED)));

        mvc.perform(post("/accounts/{id}/credits", ACCOUNT_ID)
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/" + ACCOUNT_ID + "/balance"))
                .andExpect(header().doesNotExist(RestHeaders.IDEMPOTENCY_REPLAYED))
                .andExpect(jsonPath("$.transactionId").value("TX-1"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.balances[0].accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.balances[0].balance").value(1500));
    }

    @Test
    void idempotencyKeyBecomesTheTransactionId() throws Exception {
        given(creditUseCase.handle(any(CreditCommand.class)))
                .willReturn(Result.success(outcome(OutcomeStatus.APPLIED)));

        mvc.perform(post("/accounts/{id}/credits", ACCOUNT_ID)
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500}"))
                .andExpect(status().isCreated());

        verify(creditUseCase)
                .handle(argThat((CreditCommand c) -> c.accountId().equals(ACCOUNT_ID)
                        && c.amount() == 500
                        && c.transactionId().equals("TX-42")));
    }

    @Test
    void replayedCreditKeeps201AndFlagsIdempotencyReplayed() throws Exception {
        given(creditUseCase.handle(any(CreditCommand.class)))
                .willReturn(Result.success(outcome(OutcomeStatus.REPLAYED)));

        mvc.perform(post("/accounts/{id}/credits", ACCOUNT_ID)
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/" + ACCOUNT_ID + "/balance"))
                .andExpect(header().string(RestHeaders.IDEMPOTENCY_REPLAYED, "true"))
                .andExpect(jsonPath("$.status").value("REPLAYED"));
    }

    @Test
    void missingIdempotencyKeyIsAProblemDetail400() throws Exception {
        mvc.perform(post("/accounts/{id}/credits", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 500}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_ID"));
    }

    @Test
    void nonPositiveAmountIsAProblemDetail400() throws Exception {
        mvc.perform(post("/accounts/{id}/credits", ACCOUNT_ID)
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
    }

    @Test
    void unreadableBodyIsAProblemDetail400() throws Exception {
        mvc.perform(post("/accounts/{id}/credits", ACCOUNT_ID)
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void debitAppliesAndReturns201() throws Exception {
        given(debitUseCase.handle(any(DebitCommand.class))).willReturn(Result.success(outcome(OutcomeStatus.APPLIED)));

        mvc.perform(post("/accounts/{id}/debits", ACCOUNT_ID)
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 200}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/" + ACCOUNT_ID + "/balance"))
                .andExpect(jsonPath("$.transactionId").value("TX-1"));
    }

    @Test
    void insufficientFundsDebitIsAProblemDetail422() throws Exception {
        given(debitUseCase.handle(any(DebitCommand.class)))
                .willReturn(Result.failure(new DomainError(ErrorCode.INSUFFICIENT_FUNDS, "balance 100 < 200")));

        mvc.perform(post("/accounts/{id}/debits", ACCOUNT_ID)
                        .header(RestHeaders.IDEMPOTENCY_KEY, "TX-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 200}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(header().doesNotExist("Location"));
    }

    private static CommandOutcome outcome(OutcomeStatus status) {
        return new CommandOutcome(
                new TransactionId("TX-1"),
                status,
                List.of(new AccountBalance(
                        AccountId.of(ACCOUNT_ID).orElseThrow(), new Money(BigDecimal.valueOf(1500)))));
    }
}
