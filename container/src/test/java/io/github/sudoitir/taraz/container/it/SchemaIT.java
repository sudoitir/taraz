package io.github.sudoitir.taraz.container.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-0014: Liquibase migrations apply cleanly and idempotently against real PostgreSQL. Proves the
 * changesets this change adds are actually well-formed SQL, and that the two constraint names the
 * ADR-0048 persistence failure translator matches by string really exist — a silent rename there
 * would otherwise downgrade a 409 into an opaque 500 with nothing catching it.
 *
 * <p>Deliberately does not boot a Spring context ({@link TarazDockerTest}, not
 * {@link TarazIntegrationTest}): this test only needs Liquibase and JDBC, and the full application
 * context cannot start until every outbound port has a production bean.
 */
@TarazDockerTest
class SchemaIT extends AbstractTarazIT {

    @BeforeAll
    static void applyMigrations() throws Exception {
        migrate();
    }

    @Test
    void secondMigrationRunAppliesNoChangesets() throws Exception {
        int before = changesetCount();
        migrate();
        assertThat(changesetCount()).isEqualTo(before);
    }

    @Test
    void balanceColumnIsUnboundedNumeric() throws Exception {
        assertThat(columnDataType("account", "balance_minor_units")).isEqualTo("numeric");
        assertThat(columnDataType("ledger_entry", "amount_minor_units")).isEqualTo("numeric");
    }

    @Test
    void theTwoLoadBearingConstraintNamesExist() throws Exception {
        assertThat(constraintExists("processed_transaction", "pk_processed_transaction"))
                .isTrue();
        assertThat(constraintExists("ledger_transaction", "uq_ledger_transaction_external_id"))
                .isTrue();
    }

    private static void migrate() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            Database database =
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.xml", new ClassLoaderResourceAccessor(), database)) {
                liquibase.update();
            }
        }
    }

    private static int changesetCount() throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM databasechangelog")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String columnDataType(String table, String column) throws Exception {
        String sql = """
                SELECT data_type FROM information_schema.columns
                WHERE table_name = ? AND column_name = ?
                """;
        try (Connection connection = POSTGRES.createConnection("");
                var ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static boolean constraintExists(String table, String constraintName) throws Exception {
        String sql = """
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_name = ? AND constraint_name = ?
                """;
        try (Connection connection = POSTGRES.createConnection("");
                var ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, constraintName);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }
}
