package com.spendio.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

/**
 * Resolves one MySQL 8 datasource for integration tests, in precedence order:
 * explicit {@code SPENDIO_TEST_DB_*} environment variables (developer or CI provided),
 * Testcontainers {@code mysql:8.4} when Docker is reachable, or a local MySQL on
 * port 3307 (see README). {@link #available()} gates the DB-backed tests so a machine
 * without any of the three skips them instead of failing the build.
 */
public final class MySqlTestSupport {

    private static final Logger log = LoggerFactory.getLogger(MySqlTestSupport.class);

    static final String DATABASE = "spendio_test";

    private static final DataSourceInfo RESOLVED = resolve();

    private MySqlTestSupport() {
    }

    public static boolean available() {
        return RESOLVED != null;
    }

    public static Map<String, Object> datasourceProperties() {
        if (RESOLVED == null) {
            throw new IllegalStateException("no test database available");
        }
        return Map.of(
                "spring.datasource.url", RESOLVED.url(),
                "spring.datasource.username", RESOLVED.username(),
                "spring.datasource.password", RESOLVED.password());
    }

    private static DataSourceInfo resolve() {
        DataSourceInfo fromEnvironment = fromEnvironment();
        if (fromEnvironment != null) {
            log.info("test database: environment ({})", fromEnvironment.url());
            return fromEnvironment;
        }
        DataSourceInfo fromDocker = fromDocker();
        if (fromDocker != null) {
            log.info("test database: testcontainers ({})", fromDocker.url());
            return fromDocker;
        }
        DataSourceInfo fromLocal = fromLocal();
        if (fromLocal != null) {
            log.info("test database: local instance ({})", fromLocal.url());
            return fromLocal;
        }
        log.warn("no test database found - DB-backed tests will be skipped");
        return null;
    }

    private static DataSourceInfo fromEnvironment() {
        String url = System.getenv("SPENDIO_TEST_DB_URL");
        if (url == null || url.isBlank()) {
            return null;
        }
        String username = envOrDefault("SPENDIO_TEST_DB_USERNAME", "root");
        String password = envOrDefault("SPENDIO_TEST_DB_PASSWORD", "");
        DataSourceInfo info = new DataSourceInfo(url, username, password);
        return probe(info) ? info : null;
    }

    private static DataSourceInfo fromDocker() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return null;
            }
            return MySqlTestContainer.start();
        } catch (Throwable ex) {
            log.info("docker unavailable for tests: {}", ex.getMessage());
            return null;
        }
    }

    private static DataSourceInfo fromLocal() {
        DataSourceInfo local = new DataSourceInfo(
                "jdbc:mysql://localhost:3307/" + DATABASE
                        + "?createDatabaseIfNotExist=true&connectTimeout=2000&socketTimeout=5000"
                        + "&characterEncoding=UTF-8&connectionTimeZone=UTC"
                        + "&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true",
                "root",
                "");
        return probe(local) ? local : null;
    }

    private static boolean probe(DataSourceInfo candidate) {
        Properties properties = new Properties();
        properties.setProperty("user", candidate.username());
        properties.setProperty("password", candidate.password());
        properties.setProperty("connectTimeout", "2000");
        properties.setProperty("socketTimeout", "5000");
        try (Connection connection = DriverManager.getConnection(candidate.url(), properties)) {
            return connection.isValid(3);
        } catch (SQLException ex) {
            log.debug("probe failed for {}: {}", candidate.url(), ex.getMessage());
            return false;
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    record DataSourceInfo(String url, String username, String password) {
    }
}
