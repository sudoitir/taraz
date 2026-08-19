package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.TransferUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADR-0042/0045: direct proof — not inference from the absence of a deadlock — that the smaller
 * canonical {@link AccountId} is locked first, regardless of which account a transfer names as source
 * or destination.
 */
@TarazIntegrationTest
class CanonicalLockOrderIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private TransferUseCase transfer;

    @Test
    void transferLocksTheCanonicallySmallerAccountFirstRegardlessOfDirection() throws Exception {
        AccountId first = createAccount.handle().orElseThrow().accountId();
        AccountId second = createAccount.handle().orElseThrow().accountId();
        // Canonical order (ADR-0042), not creation order.
        AccountId smaller = first.compareTo(second) <= 0 ? first : second;
        AccountId larger = smaller.equals(first) ? second : first;

        credit.handle(new CreditCommand(larger.toString(), 1000, "SEED-" + UUID.randomUUID()))
                .orElseThrow();

        try (Connection side = POSTGRES.createConnection("")) {
            side.setAutoCommit(false);
            try (var ps = side.prepareStatement("SELECT id FROM account WHERE id = ? FOR UPDATE")) {
                ps.setObject(1, smaller.value());
                ps.executeQuery();
            }

            // A transfer naming the LARGER account as source and the locked SMALLER one as
            // destination still must lock the smaller one first (ADR-0042's ordering is by identity,
            // never by source/destination role) — so it blocks on the held lock and times out.
            Result<?> result = transfer.handle(
                    new TransferCommand(larger.toString(), smaller.toString(), 1, "TX-" + UUID.randomUUID()));

            assertThat(result.isFailure()).isTrue();
            assertThat(result.error().orElseThrow().code()).isEqualTo(ErrorCode.CONCURRENCY_CONFLICT);

            side.rollback();
        }
    }
}
