package com.spendio.sharedkernel.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendio.config.SecurityProperties;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bearer-token rejection beyond the tampered/absent cases (AuthSecurityTest):
 * a correctly-signed token whose lifetime has passed must answer {@code AUTH_TOKEN_EXPIRED},
 * and a token signed for the wrong {@code kid} must answer {@code AUTH_TOKEN_INVALID} -
 * both through the real filter chain against a real database.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class ExpiredTokenTest extends AbstractIntegrationTest {

    private static final String PROTECTED_ROUTE = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SecurityProperties security;
    @Autowired
    private ProfileService profileService;

    @Test
    void expiredButCorrectlySignedTokenIsRejectedAsExpired() throws Exception {
        mockMvc.perform(get(PROTECTED_ROUTE)
                        .header("Authorization", "Bearer " + signedToken(security.jwt().kid(), true, 1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void correctlySignedTokenWithUnknownKidIsRejectedAsInvalid() throws Exception {
        mockMvc.perform(get(PROTECTED_ROUTE)
                        .header("Authorization", "Bearer " + signedToken("not-the-kid", false, 1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void liveTokenOfTheSameShapeOpensTheRoute() throws Exception {
        UserResponse user = profileService.create(new CreateUserCommand(
                "expired-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Jwt", "Keeper",
                "UTC", "en-US", "USD"));

        mockMvc.perform(get(PROTECTED_ROUTE)
                        .header("Authorization", "Bearer " + signedToken(security.jwt().kid(), false, user.id())))
                .andExpect(status().isOk());
    }

    /**
     * Mints a token with the exact header the server expects ({@code typ=access} plus the
     * production {@code kid}) signed with the real test secret, so the only variable that
     * can sink it is the expiration timestamp (or the caller's {@code kid}).
     */
    private String signedToken(String kid, boolean expired, long subjectId) {
        SecretKey key = Keys.hmacShaKeyFor(security.jwt().secret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(kid).type("access").and()
                .subject(String.valueOf(subjectId))
                .issuedAt(Date.from(now.minus(Duration.ofMinutes(30))))
                .expiration(Date.from(expired ? now.minus(Duration.ofMinutes(10)) : now.plus(Duration.ofHours(1))))
                .id(UUID.randomUUID().toString())
                .claim("email", "expired@spendio.test")
                .claim("emailVerified", true)
                .claim("sid", UUID.randomUUID().toString())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}