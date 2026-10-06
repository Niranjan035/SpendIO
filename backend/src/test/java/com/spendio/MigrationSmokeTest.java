package com.spendio;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Migration smoke test (ARCHITECTURE.md Phase 0): V1 applies cleanly on real MySQL and
 * leaves the schema "empty but migrated" - charset/collation contract in place,
 * application tables arriving with V2+ (ADR-0005).
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
        assertThat(versions).containsExactly("1");

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
    void schemaIsMigratedButEmpty() {
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME <> 'flyway_schema_history'",
                Integer.class);
        assertThat(tables).isZero();
    }
}
