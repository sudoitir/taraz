package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreateAccountUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.CreditUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitCommand;
import io.github.sudoitir.taraz.core.application.ports.inbound.DebitUseCase;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceQuery;
import io.github.sudoitir.taraz.core.application.ports.inbound.GetBalanceUseCase;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.common.ErrorCode;
import io.github.sudoitir.taraz.core.domain.common.Result;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * ADR-0018/0021: a failed operation's atomic unit commits nothing. An insufficient-funds debit must
 * leave the balance untouched and no row anywhere — {@code ledger_transaction}, {@code ledger_entry},
 * {@code processed_transaction}, {@code outbox} — for its {@code transactionId}.
 */
@TarazIntegrationTest
class RollbackLeavesNoTraceIT extends AbstractTarazIT {

    @Autowired
    private CreateAccountUseCase createAccount;

    @Autowired
    private CreditUseCase credit;

    @Autowired
    private DebitUseCase debit;

    @Autowired
    private GetBalanceUseCase getBalance;

    @Test
    void insufficientFundsDebitLeavesNoTraceAnywhere() throws Exception {
        AccountId account = createAccount.handle().orElseThrow().accountId();
        credit.handle(new CreditCommand(account.toString(), 500, "SEED-" + UUID.randomUUID()))
                .orElseThrow();
        String txId = "TX-FAIL-" + UUID.randomUUID();

        Result<?> result = debit.handle(new DebitCommand(account.toString(), 700, txId));

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error().orElseThrow().code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        BalanceView balance =
                getBalance.handle(new GetBalanceQuery(account.toString())).orElseThrow();
        assertThat(balance.balance()).isEqualTo(Money.of(500).orElseThrow());

        try (Connection conn = POSTGRES.createConnection("")) {
            assertThat(countByExternalId(conn, "ledger_transaction", "external_id", txId))
                    .isZero();
            assertThat(countByExternalId(conn, "processed_transaction", "transaction_id", txId))
                    .isZero();
            assertThat(countByExternalId(conn, "outbox", "transaction_id", txId))
                    .isZero();
        }
    }

    private static int countByExternalId(Connection conn, String table, String column, String value) throws Exception {
        String sql = "SELECT count(*) FROM " + table + " WHERE " + column + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
