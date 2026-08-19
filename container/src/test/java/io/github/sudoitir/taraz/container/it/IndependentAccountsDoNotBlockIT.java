package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Deterministic (not timing-based-and-hopeful) proof that independent accounts never block each other
 * (ADR-0026/0045): a side connection holds a real row lock on account A via raw JDBC, outside the
 * application entirely. A concurrent {@code credit(B)} must complete quickly — proving B truly isn't
 * blocked. A concurrent {@code credit(A)} must then fail with {@code CONCURRENCY_CONFLICT} once
 * ADR-0046's {@code lock_timeout} (3s) elapses — proving A really was locked, so (a) is a genuine
 * non-blocking result rather than a lucky race.
 */
@TarazIntegrationTest
class IndependentAccountsDoNotBlockIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Test
    void unrelatedAccountProceedsWhileAnotherIsLocked() throws Exception {
        AccountId a = createAccount.handle().orElseThrow().accountId();
        AccountId b = createAccount.handle().orElseThrow().accountId();

        try (Connection side = POSTGRES.createConnection("")) {
            side.setAutoCommit(false);
            try (var ps = side.prepareStatement("SELECT id FROM account WHERE id = ? FOR UPDATE")) {
                ps.setObject(1, a.value());
                ps.executeQuery();
            }

            // B is unrelated to the held lock — must complete well within lock_timeout.
            CompletableFuture<Result<?>> creditB = CompletableFuture.supplyAsync(
                    () -> credit.handle(new CreditCommand(b.toString(), 100, "TX-B-" + UUID.randomUUID())));
            Result<?> resultB = creditB.get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(resultB.isSuccess())
                    .as("credit on the unrelated account must not be blocked by A's lock")
                    .isTrue();

            // A is genuinely locked — this must fail once lock_timeout (3s) elapses, proving the lock
            // was real rather than the fast result above being a lucky race.
            Result<?> resultA = credit.handle(new CreditCommand(a.toString(), 100, "TX-A-" + UUID.randomUUID()));
            assertThat(resultA.isFailure()).isTrue();
            assertThat(resultA.error().orElseThrow().code()).isEqualTo(ErrorCode.CONCURRENCY_CONFLICT);

            side.rollback();
        }
    }
}
