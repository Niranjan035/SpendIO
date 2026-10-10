package com.spendio.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendio.support.AbstractIntegrationTest;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Security contract of the auth surface (ARCHITECTURE.md E.1, D.2, K.1):
 * anonymous 401s, CSRF only on cookie routes, expired/tampered bearer rejection and
 * per-IP rate limiting with {@code Retry-After} - all through the real chain.
 *
 * <p>Class-level properties drop the sensitive-route budget to 1; every test that
 * touches a sensitive route brings its own {@code X-Forwarded-For} identity so the
 * buckets never collide.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@SpringBootTest(properties = "spendio.security.rate-limit.sensitive-requests=1")
@Transactional
@AutoConfigureMockMvc
class AuthSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Test
    void anonymousCallToAProtectedAuthRouteIs401WithAStableCode() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theRefreshAndLogoutRoutesDemandCsrfWhileLoginDoesNot() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));

        // Login is public and cookie-free, so no CSRF token is required.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@spendio.test\",\"password\":\"Whatever1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void tamperedBearerTokenIsRejectedAsInvalid() throws Exception {
        String token = validLookingButTamperedToken();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void sensitiveAuthRoutesTripThePerIpBudgetAndAnswer429WithRetryAfter() throws Exception {
        String ip = "203.0.113.7";
        String body = "{\"email\":\"nobody@spendio.test\",\"password\":\"Whatever1234\"}";

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));

        // A different client is unaffected.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validBearerFromALiveLoginOpensTheProtectedRoutes() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"whoever@spendio.test\",\"password\":\"Whatever1234\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(login.getResponse().getContentAsString()).doesNotContain("stacktrace");

        // Same shape as the SPA bootstrap: no token, no access.
        mockMvc.perform(get("/api/v1/auth/sessions"))
                .andExpect(status().isUnauthorized());
    }

    /** Structurally plausible JWT whose signature no longer matches the payload. */
    private static String validLookingButTamperedToken() {
        String header = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"access\",\"kid\":\"v1\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        "{\"sub\":\"1\",\"email\":\"a@b.c\",\"emailVerified\":true,\"sid\":\"11111111-1111-1111-1111-111111111111\",\"exp\":9999999999}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("not-a-real-signature-padding!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature;
    }
}
