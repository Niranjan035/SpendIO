package com.spendio;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Context-loads test (ARCHITECTURE.md Phase 0) against real MySQL with an embedded
 * server, proving the runtime wiring slices cannot: dialect resolution, servlet filter
 * registration and the security entry point on an actual HTTP response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@AutoConfigureTestRestTemplate
class SpendioApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TestRestTemplate rest;

    @Test
    void contextLoadsAndUsesMySqlDialect() {
        Dialect dialect = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect();
        assertThat(dialect.getClass().getName()).contains("MySQL");
    }

    @Test
    void unauthenticatedRequestIsRejectedWithRequestIdHeader() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/transactions", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).matches("[0-9a-f]{32}");
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getHeaders().getContentType().isCompatibleWith(
                org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody()).contains("\"code\":\"AUTH_TOKEN_MISSING\"");
        assertThat(response.getBody()).contains("\"traceId\"");
    }
}
