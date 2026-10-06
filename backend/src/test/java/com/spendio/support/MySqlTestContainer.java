package com.spendio.support;

import org.testcontainers.containers.MySQLContainer;

/**
 * One {@code mysql:8.4} container per JVM, started lazily by {@link MySqlTestSupport}
 * (ARCHITECTURE.md Phase 0 - migration smoke tests run against real MySQL).
 */
public final class MySqlTestContainer {

    private static MySQLContainer<?> container;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(MySqlTestContainer::stop, "test-mysql-shutdown"));
    }

    private MySqlTestContainer() {
    }

    static synchronized MySqlTestSupport.DataSourceInfo start() {
        if (container == null) {
            container = new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName(MySqlTestSupport.DATABASE)
                    .withUsername("spendio")
                    .withPassword("spendio");
            container.start();
        }
        return new MySqlTestSupport.DataSourceInfo(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    static synchronized void stop() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
