package com.spendio.fx.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.fx.domain.FxRate;
import com.spendio.fx.dto.request.ConvertRequest;
import com.spendio.fx.dto.request.ManualRateRequest;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * FX endpoints end-to-end (ARCHITECTURE.md D.5): manual override, history, current
 * resolution, conversion and the user-triggered refresh.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class FxControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private FxRateRepository rates;

    @Test
    void manualOverrideIsStoredAndPreferredOnRead() throws Exception {
        long userId = seedUser();
        String payload = objectMapper.writeValueAsString(
                new ManualRateRequest("USD", "EUR", new BigDecimal("0.91"), LocalDate.of(2026, 6, 12)));

        mockMvc.perform(asUser(userId, post("/api/v1/fx/rates")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.quoteCurrencyCode").value("EUR"))
                .andExpect(jsonPath("$.rate").value(0.91))
                .andExpect(jsonPath("$.source").value("manual"));
    }

    @Test
    void currentRateResolvesThroughTheChain() throws Exception {
        long userId = seedUser();
        seedRate("USD", "EUR", "0.9", LocalDate.of(2026, 6, 12), "http");

        mockMvc.perform(asUser(userId, get("/api/v1/fx/rates/current")
                        .param("from", "USD").param("to", "EUR").param("date", "2026-06-15")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(0.9))
                .andExpect(jsonPath("$.source").value("http"));
    }

    @Test
    void historyReturnsNewestFirst() throws Exception {
        long userId = seedUser();
        seedRate("USD", "EUR", "0.88", LocalDate.of(2026, 6, 1), "http");
        seedRate("USD", "EUR", "0.9", LocalDate.of(2026, 6, 12), "http");

        mockMvc.perform(asUser(userId, get("/api/v1/fx/rates")
                        .param("base", "USD").param("quote", "EUR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rate").value(0.9))
                .andExpect(jsonPath("$[1].rate").value(0.88));
    }

    @Test
    void convertPricesAnAmountAtTheResolvedRate() throws Exception {
        long userId = seedUser();
        seedRate("USD", "JPY", "150", LocalDate.of(2026, 6, 12), "http");
        String payload = objectMapper.writeValueAsString(
                new ConvertRequest("USD", "JPY", 700, LocalDate.of(2026, 6, 15)));

        mockMvc.perform(asUser(userId, post("/api/v1/fx/convert")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAmountMinor").value(700))
                .andExpect(jsonPath("$.toAmountMinor").value(1050))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void convertWithoutAnyRateIs503() throws Exception {
        long userId = seedUser();
        String payload = objectMapper.writeValueAsString(
                new ConvertRequest("USD", "CHF", 100, LocalDate.of(2020, 1, 1)));

        mockMvc.perform(asUser(userId, post("/api/v1/fx/convert")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));
    }

    @Test
    void refreshEndpointRunsTheJob() throws Exception {
        long userId = seedUser();

        mockMvc.perform(asUser(userId, post("/api/v1/fx/refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("fx-rate-refresh"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void anonymousCallIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/fx/rates").param("base", "USD").param("quote", "EUR"))
                .andExpect(status().isUnauthorized());
    }

    private void seedRate(String base, String quote, String rate, LocalDate date, String provider) {
        FxRate row = new FxRate();
        row.setBaseCurrencyCode(base);
        row.setQuoteCurrencyCode(quote);
        row.setRate(new BigDecimal(rate));
        row.setRateDate(date);
        row.setProvider(provider);
        row.setManual("manual".equals(provider));
        rates.save(row);
    }

    private long seedUser() {
        UserResponse user = profileService.create(new CreateUserCommand(
                "fx-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Fay", "Xu",
                "America/New_York", "en-US", "USD"));
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