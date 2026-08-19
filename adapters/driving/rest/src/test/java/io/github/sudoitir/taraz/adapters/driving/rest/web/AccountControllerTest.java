package io.github.sudoitir.taraz.adapters.driving.rest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.sudoitir.taraz.adapters.driving.rest.mapper.RestMapperImpl;
import io.github.sudoitir.taraz.adapters.driving.rest.problem.ProblemFactory;
import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.DomainError;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AccountController.class,
        properties = {
            "spring.jackson.property-naming-strategy=LOWER_CAMEL_CASE",
            "spring.mvc.problemdetails.enabled=true"
        })
@Import({ProblemFactory.class, RestMapperImpl.class})
class AccountControllerTest {

    private static final String ACCOUNT_ID = "123e4567-e89b-42d3-a456-426614174000";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CreateAccountUseCase createAccount;

    @MockitoBean
    private GetBalanceUseCase getBalance;

    @Test
    void postAccountsCreatesWithServerAssignedIdAndZeroBalance() throws Exception {
        given(createAccount.handle()).willReturn(Result.success(view(0)));

        mvc.perform(post("/accounts"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/accounts/" + ACCOUNT_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void getBalanceReturnsSnakeCaseBodyAndIsNeverCacheable() throws Exception {
        given(getBalance.handle(any(GetBalanceQuery.class))).willReturn(Result.success(view(1000)));

        mvc.perform(get("/accounts/{id}/balance", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.balance").value(1000));
    }

    @Test
    void unknownAccountIsAProblemDetail404() throws Exception {
        given(getBalance.handle(any(GetBalanceQuery.class)))
                .willReturn(Result.failure(new DomainError(ErrorCode.ACCOUNT_NOT_FOUND, "no account " + ACCOUNT_ID)));

        mvc.perform(get("/accounts/{id}/balance", ACCOUNT_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:taraz:problem:account-not-found"))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void malformedAccountIdIsAProblemDetail400() throws Exception {
        given(getBalance.handle(any(GetBalanceQuery.class)))
                .willReturn(Result.failure(new DomainError(ErrorCode.INVALID_ACCOUNT_ID, "not a UUID")));

        mvc.perform(get("/accounts/{id}/balance", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACCOUNT_ID"));
    }

    @Test
    void incomingCorrelationIdIsEchoed() throws Exception {
        given(getBalance.handle(any(GetBalanceQuery.class))).willReturn(Result.success(view(1000)));

        mvc.perform(get("/accounts/{id}/balance", ACCOUNT_ID).header(RestHeaders.X_CORRELATION_ID, "corr-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RestHeaders.X_CORRELATION_ID, "corr-123"));
    }

    @Test
    void correlationIdIsGeneratedWhenAbsent() throws Exception {
        given(getBalance.handle(any(GetBalanceQuery.class))).willReturn(Result.success(view(1000)));

        String correlationId = mvc.perform(get("/accounts/{id}/balance", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(RestHeaders.X_CORRELATION_ID);

        assertThat(correlationId).isNotNull();
        assertThat(UUID.fromString(correlationId)).isNotNull();
    }

    @Test
    void unexpectedFailureIsAnOpaque500() throws Exception {
        given(getBalance.handle(any(GetBalanceQuery.class))).willThrow(new RuntimeException("db is on fire"));

        mvc.perform(get("/accounts/{id}/balance", ACCOUNT_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    private static BalanceView view(long balance) {
        return new BalanceView(AccountId.of(ACCOUNT_ID).orElseThrow(), new Money(BigDecimal.valueOf(balance)));
    }
}
