package com.spendio.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.auth.domain.TokenPurpose;
import com.spendio.auth.domain.VerificationToken;
import com.spendio.auth.dto.request.RegisterRequest;
import com.spendio.auth.dto.request.ResetPasswordRequest;
import com.spendio.auth.repository.PasswordResetTokenRepository;
import com.spendio.auth.repository.VerificationTokenRepository;
import com.spendio.auth.security.TokenCodec;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * The auth surface end-to-end (ARCHITECTURE.md E.2, D.2): registration, login,
 * rotation with reuse detection, logout, password reset/verification flows and the
 * session list - exercised through the real security chain against MySQL.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Str0ngPassphrase1";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void registerCreatesTheAccountSetsTheHttpOnlyCookieAndAnswersTheMeEndpoint() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "Rita@Spendio.test", PASSWORD, "Rita", "Silva", "Europe/Lisbon", "en-GB", "EUR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("rita@spendio.test"))
                .andExpect(jsonPath("$.user.baseCurrencyCode").value("EUR"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        Cookie refresh = register.getResponse().getCookie("spendio_rt");
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(register.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("spendio_rt") && header.contains("SameSite=Strict"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken(register)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("rita@spendio.test"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    @Test
    void registerRejectsWeakPasswordsAndDuplicateEmails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"weak@spendio.test\",\"password\":\"short\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"))
                .andExpect(jsonPath("$.violations[0]").value("at-least-10-characters"));

        seedUser("taken@spendio.test");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"taken@spendio.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EMAIL_TAKEN"));
    }

    @Test
    void loginRefreshRotationAndReuseDetection() throws Exception {
        seedUser("marta@spendio.test", PASSWORD);
        Cookie refresh = login("marta@spendio.test", PASSWORD);

        Cookie xsrf = csrfCookie();
        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotatedCookie = rotated.getResponse().getCookie("spendio_rt");
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(refresh.getValue());

        // Presenting the already-rotated cookie is the theft signal.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"));

        // ...and the whole family died with it, including the newest member.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rotatedCookie, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"));
    }

    @Test
    void refreshRequiresTheCsrfDoubleSubmit() throws Exception {
        Cookie refresh = loginResponseCookie("sara@spendio.test");

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refresh))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        seedUser("nuno@spendio.test", PASSWORD);
        MvcResult loginResult = loginRequest("nuno@spendio.test", PASSWORD);
        Cookie refresh = loginResult.getResponse().getCookie("spendio_rt");
        String token = accessToken(loginResult);
        Cookie xsrf = csrfCookie();

        // Logout is a bearer-authenticated route that also demands the CSRF double-submit.
        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token)
                        .cookie(refresh, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("spendio_rt=;"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgotPasswordAlwaysAnswers202AndResetRotatesThePassword() throws Exception {
        Long userId = seedUser("ivo@spendio.test", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@spendio.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty());

        String raw = "known-reset-token-raw";
        passwordResetTokenRepository.save(com.spendio.auth.domain.PasswordResetToken.issue(
                userId,
                TokenCodec.sha256Hex(raw),
                Instant.now().plus(java.time.Duration.ofMinutes(30)),
                "1.2.3.4"));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequest(raw, "Br4ndNewPassphrase"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ivo@spendio.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ivo@spendio.test\",\"password\":\"Br4ndNewPassphrase\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordKeepsTheCurrentSessionAndNeedsTheCurrentPassword() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hugo@spendio.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = accessToken(register);
        Cookie refresh = register.getResponse().getCookie("spendio_rt");

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"Br4ndNewPassphrase\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Br4ndNewPassphrase\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hugo@spendio.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        Cookie xsrf = csrfCookie();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refresh, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void sessionsListMarksTheCurrentDeviceAndRevocationIsOwnerScoped() throws Exception {
        seedUser("clara@spendio.test", PASSWORD);
        Cookie first = login("clara@spendio.test", PASSWORD);
        MvcResult secondLogin = loginRequest("clara@spendio.test", PASSWORD);
        Cookie second = secondLogin.getResponse().getCookie("spendio_rt");
        String tokenOfSecond = accessToken(secondLogin);

        MvcResult list = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + tokenOfSecond))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.current == true)]").exists())
                .andReturn();
        String otherId = nonCurrentSessionId(list);

        // A second account cannot revoke someone else's session (404, never 403).
        seedUser("dana@spendio.test", PASSWORD);
        String outsiderToken = loginToken("dana@spendio.test", PASSWORD);

        mockMvc.perform(delete("/api/v1/auth/sessions/" + otherId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/auth/sessions/" + otherId)
                        .header("Authorization", "Bearer " + tokenOfSecond))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + tokenOfSecond))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        assertThat(first.getValue()).isNotBlank();
        assertThat(second.getValue()).isNotBlank();
    }

    private static String nonCurrentSessionId(MvcResult list) throws Exception {
        var sessions = new ObjectMapper().readTree(list.getResponse().getContentAsString());
        for (var session : sessions) {
            if (!session.get("current").asBoolean()) {
                return session.get("id").asText();
            }
        }
        throw new AssertionError("expected a non-current session in " + sessions);
    }

    @Test
    void emailVerificationConsumesTheTokenOnce() throws Exception {
        MvcResult register = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"veda@spendio.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = accessToken(register);
        Long userId = userId(token);

        String raw = "known-verify-token-raw";
        verificationTokenRepository.save(VerificationToken.issue(
                userId,
                TokenPurpose.EMAIL_VERIFY,
                TokenCodec.sha256Hex(raw),
                Instant.now().plus(java.time.Duration.ofHours(24)),
                null));

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email address verified"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));

        // Single use: the same token cannot be replayed.
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + raw + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void logoutAllEndsEverySession() throws Exception {
        seedUser("tomi@spendio.test", PASSWORD);
        Cookie first = login("tomi@spendio.test", PASSWORD);
        Cookie second = login("tomi@spendio.test", PASSWORD);
        String token = loginToken("tomi@spendio.test", PASSWORD);
        Cookie xsrf = csrfCookie();

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header("Authorization", "Bearer " + token)
                        .cookie(first, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isNoContent());

        for (Cookie cookie : new Cookie[] {first, second}) {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(cookie, xsrf)
                            .header("X-XSRF-TOKEN", xsrf.getValue()))
                    .andExpect(status().isUnauthorized());
        }
    }

    private MvcResult loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Cookie login(String email, String password) throws Exception {
        return loginRequest(email, password).getResponse().getCookie("spendio_rt");
    }

    private String loginToken(String email, String password) throws Exception {
        return accessToken(loginRequest(email, password));
    }

    private Cookie loginResponseCookie(String email) throws Exception {
        seedUser(email, PASSWORD);
        return login(email, PASSWORD);
    }

    private static String accessToken(MvcResult result) throws Exception {
        return new ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private static Long userId(String accessToken) throws Exception {
        String[] parts = accessToken.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(payload).get("sub").asLong();
    }

    private Cookie csrfCookie() throws Exception {
        MvcResult initial = mockMvc.perform(get("/api/v1/currencies")).andReturn();
        Cookie cookie = initial.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private Long seedUser(String email) {
        return seedUser(email, PASSWORD);
    }

    private Long seedUser(String email, String password) {
        UserResponse user = profileService.create(new CreateUserCommand(
                email, passwordEncoder.encode(password), null, null, "UTC", "en-GB", "USD"));
        return user.id();
    }
}
