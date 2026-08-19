package io.github.sudoitir.taraz.adapters.driven.persistence.account;

import io.github.sudoitir.taraz.core.application.ports.inbound.BalanceView;
import io.github.sudoitir.taraz.core.application.ports.outbound.AccountBalanceReadRepository;
import io.github.sudoitir.taraz.core.domain.account.AccountId;
import io.github.sudoitir.taraz.core.domain.money.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * ADR-0007: the read side's own outbound port, deliberately on plain {@code JdbcClient} rather than
 * JPA/{@code EntityManager}. This is the one place "never opens a transaction or takes a lock" is a
 * hard contract, and the only way to guarantee it structurally is to issue a single statement with
 * nothing ambient to join — using the {@code EntityManager} here would let a balance read join
 * whatever transaction happened to be active and land the row in a persistence context that could
 * later be dirty-checked.
 */
@Repository
public class AccountBalanceJdbcReadRepository implements AccountBalanceReadRepository {

    private static final String SQL = "SELECT id, balance_minor_units FROM account WHERE id = ?";

    private final JdbcClient jdbc;

    public AccountBalanceJdbcReadRepository(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<BalanceView> findByAccountId(AccountId id) {
        return jdbc.sql(SQL)
                .param(id.value())
                .query((rs, rowNum) -> new BalanceView(
                        new AccountId(rs.getObject("id", UUID.class)),
                        new Money(rs.getBigDecimal("balance_minor_units"))))
                .optional();
    }
}
