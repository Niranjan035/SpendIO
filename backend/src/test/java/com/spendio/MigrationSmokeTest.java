package com.spendio;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migration smoke test (ARCHITECTURE.md Phase 0): every migration applies cleanly on real
 * MySQL and leaves the schema in the state the following phase expects - charset/collation
 * contract in place, Phase 1 tables (V2/V3) populated with the currency seed (ADR-0005).
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
class MigrationSmokeTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void baselineIsAppliedSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17");

        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class);
        assertThat(failed).isZero();
    }

    @Test
    void schemaUsesUtf8mb4WithModernCollation() {
        String charset = jdbc.queryForObject(
                "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()",
                String.class);
        String collation = jdbc.queryForObject(
                "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE()",
                String.class);

        assertThat(charset).isEqualTo("utf8mb4");
        assertThat(collation).isEqualTo("utf8mb4_0900_ai_ci");
    }

    @Test
    void phaseOneTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history' "
                        + "ORDER BY TABLE_NAME",
                String.class);
        assertThat(tables).contains(
                "currencies", "password_reset_tokens", "user_preferences", "users",
                "user_sessions", "verification_tokens",
                "accounts", "categories", "fx_rates", "transactions",
                "job_execution_log",
                "recurring_transactions", "recurring_transaction_runs",
                "budgets", "budget_alerts", "goals", "goal_contributions",
                "notifications", "notification_preferences",
                "csv_imports", "csv_import_rows", "audit_log");
    }

    @Test
    void currencySeedIsLoaded() {
        Integer currencies = jdbc.queryForObject(
                "SELECT COUNT(*) FROM currencies WHERE is_active = TRUE", Integer.class);
        assertThat(currencies).isGreaterThanOrEqualTo(30);

        String euro = jdbc.queryForObject(
                "SELECT name FROM currencies WHERE code = 'EUR'", String.class);
        assertThat(euro).isEqualTo("Euro");
    }
}
