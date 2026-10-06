package com.spendio.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for tests that need the full application context: {@code test} profile,
 * datasource supplied by {@link MySqlTestSupport} (env / Testcontainers / local MySQL).
 *
 * <p>Concrete subclasses must carry {@code @EnabledIf("com.spendio.support.MySqlTestSupport#available")}
 * on the <em>test class itself</em>: class-level conditions are not inherited from an
 * abstract superclass, so declaring it here would let the context load (and fail) on a
 * machine with no test database.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        MySqlTestSupport.datasourceProperties().forEach((key, value) -> registry.add(key, () -> value));
    }
}
