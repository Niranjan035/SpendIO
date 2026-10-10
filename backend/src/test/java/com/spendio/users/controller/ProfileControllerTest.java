package com.spendio.users.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.support.WithAuthenticatedUser;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.request.UpdatePreferencesRequest;
import com.spendio.users.domain.DecimalStyle;
import com.spendio.users.domain.SymbolPosition;
import com.spendio.users.domain.Theme;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile endpoints end-to-end (ARCHITECTURE.md Phase 1): a user can be created with
 * preferences, read back and updated; every route demands authentication.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class ProfileControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getReturnsTheProfile() throws Exception {
        long userId = seedUser("ana@example.com");

        mockMvc.perform(asUser(userId, get("/api/v1/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ana Lopes"))
                .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"));
    }

    @Test
    void anonymousCallIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAuthenticatedUser(424242L)
    void authenticatedCallerWithoutProfileGetsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void patchUpdatesProfileAndRederivesDisplayName() throws Exception {
        long userId = seedUser("ana@example.com");

        mockMvc.perform(asUser(userId, patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Anita\",\"lastName\":\"Lopes\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anita"))
                .andExpect(jsonPath("$.displayName").value("Anita Lopes"));

        mockMvc.perform(asUser(userId, get("/api/v1/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anita Lopes"));
    }

    @Test
    void patchRejectsUnknownTimezone() throws Exception {
        long userId = seedUser("ana@example.com");

        mockMvc.perform(asUser(userId, patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Nowhere/Land\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void preferencesRoundTripThroughPut() throws Exception {
        long userId = seedUser("ana@example.com");

        mockMvc.perform(asUser(userId, get("/api/v1/users/me/preferences")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("SYSTEM"))
                .andExpect(jsonPath("$.budgetAlertThresholdPct").value(80));

        mockMvc.perform(asUser(userId, put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\",\"dateFormat\":\"dd MMM yyyy\",\"firstDayOfWeek\":7,"
                                + "\"decimalStyle\":\"COMMA\",\"groupSeparator\":\",\",\"currencySymbolPosition\":\"AFTER\","
                                + "\"numberFormat\":\"#,##0.00\",\"budgetAlertThresholdPct\":90,\"goalReminderDays\":14,"
                                + "\"recurringReminderDays\":1,\"emailDigestEnabled\":true,\"reducedMotion\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.decimalStyle").value("COMMA"))
                .andExpect(jsonPath("$.currencySymbolPosition").value("AFTER"))
                .andExpect(jsonPath("$.budgetAlertThresholdPct").value(90))
                .andExpect(jsonPath("$.emailDigestEnabled").value(true));

        mockMvc.perform(asUser(userId, get("/api/v1/users/me/preferences")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalReminderDays").value(14));
    }

    @Test
    void preferencesPutRejectsOutOfRangeThreshold() throws Exception {
        long userId = seedUser("ana@example.com");
        UpdatePreferencesRequest outOfRange = new UpdatePreferencesRequest(
                Theme.SYSTEM, "yyyy-MM-dd", 1, DecimalStyle.DOT, ",",
                SymbolPosition.BEFORE, "#,##0.00", 0, 30, 3, false, false);

        mockMvc.perform(asUser(userId, put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outOfRange))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void changeBaseCurrencySwapsTheDisplayCurrency() throws Exception {
        long userId = seedUser("ana@example.com");

        mockMvc.perform(asUser(userId, put("/api/v1/users/me/base-currency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currencyCode\":\"EUR\",\"revalueExistingTransactions\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrencyCode").value("EUR"));
    }

    @Test
    void changeBaseCurrencyRejectsUnknownCode() throws Exception {
        long userId = seedUser("ana@example.com");

        mockMvc.perform(asUser(userId, put("/api/v1/users/me/base-currency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currencyCode\":\"XXX\",\"revalueExistingTransactions\":false}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CURRENCY_NOT_FOUND"));
    }

    @Test
    void currencyListIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(30)))
                .andExpect(jsonPath("$[?(@.code == 'EUR')]").exists());
    }

    private long seedUser(String email) {
        UserResponse user = profileService.create(new CreateUserCommand(
                email, "{bcrypt}", "Ana", "Lopes", "Europe/Lisbon", "en-GB", "USD"));
        return user.id();
    }

    private static MockHttpServletRequestBuilder asUser(long userId, MockHttpServletRequestBuilder builder) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user-" + userId + "@spendio.test", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return builder.with(SecurityMockMvcRequestPostProcessors.authentication(authentication));
    }
}
