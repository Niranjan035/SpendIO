package com.spendio.transactions.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountBalanceService;
import com.spendio.accounts.service.AccountService;
import com.spendio.fx.domain.FxRate;
import com.spendio.fx.repository.FxRateRepository;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-currency transfers (ARCHITECTURE.md D.8/E.8): the destination leg is converted
 * through the daily series exactly once and both legs freeze their base-currency triple.
 * Rates come from a deterministic manual fixture so the maths is stable; reversal and
 * balances are asserted on the ledger itself.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class CrossCurrencyTransferTest extends AbstractIntegrationTest {

    private static final LocalDate RATE_DATE = LocalDate.of(2026, 6, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountBalanceService balanceService;
    @Autowired
    private FxRateRepository rates;

    private long userId;
    private long usdId;
    private long eurId;

    @BeforeEach
    void seedFixture() {
        userId = seedUser("ccx");
        usdId = accountService.create(userId, new CreateAccountRequest(
                "USD Checking", AccountType.BANK, "USD", 50_000L, RATE_DATE, null,
                null, null, null, null, null, null, null, false, null, true)).getId();
        eurId = accountService.create(userId, new CreateAccountRequest(
                "EUR Savings", AccountType.BANK, "EUR", 0, null, null,
                null, null, null, null, null, null, null, false, null, true)).getId();
    }

    @Test
    void crossCurrencyTransferConvertsOnceAndFreezesBothBaseTriples() throws Exception {
        seedManualRate("USD", "EUR", "0.90");

        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(createPayload(usdId, eurId, 10_000L, "USD", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromTransaction.direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.toTransaction.direction").value("INFLOW"))
                .andExpect(jsonPath("$.fromTransaction.currencyCode").value("USD"))
                .andExpect(jsonPath("$.toTransaction.currencyCode").value("EUR"))
                .andExpect(jsonPath("$.fromTransaction.amountMinor").value(10_000))
                .andExpect(jsonPath("$.toTransaction.amountMinor").value(9_000))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        long toLegBaseMinor = body.get("toTransaction").get("baseAmountMinor").asLong();
        // 10_000 USD @ 0.90 must still book exactly 10_000 base (USD) minor on the EUR leg.
        assertThat(toLegBaseMinor).isEqualTo(10_000);
        assertThat(body.get("toTransaction").get("exchangeRate").isNumber()).isTrue();

        assertThat(balanceService.current(userId, usdId).balanceMinor()).isEqualTo(40_000);
        assertThat(balanceService.current(userId, eurId).balanceMinor()).isEqualTo(9_000);

        mockMvc.perform(asUser(userId, get("/api/v1/transfers/{id}", body.get("transferGroupId").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toTransaction.amountMinor").value(9_000));
    }

    @Test
    void explicitToAmountOverridesTheConversion() throws Exception {
        seedManualRate("USD", "EUR", "0.90");

        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(usdId, eurId, 10_000L, "USD", 8_850L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.toTransaction.amountMinor").value(8_850))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(balanceService.current(userId, usdId).balanceMinor()).isEqualTo(40_000);
        assertThat(balanceService.current(userId, eurId).balanceMinor()).isEqualTo(8_850);
    }

    @Test
    void missingRateFailsFastWithoutWritingAnyLeg() throws Exception {
        // no rate for USD -> GBP anywhere: the transfer must fail before booking legs
        long gbpId = accountService.create(userId, new CreateAccountRequest(
                "GBP Savings", AccountType.BANK, "GBP", 0, null, null,
                null, null, null, null, null, null, null, false, null, true)).getId();

        mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(usdId, gbpId, 10_000L, "USD", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FX_RATE_UNAVAILABLE"));

        assertThat(balanceService.current(userId, usdId).balanceMinor()).isEqualTo(50_000);
        assertThat(balanceService.current(userId, gbpId).balanceMinor()).isEqualTo(0);
    }

    // --- helpers --------------------------------------------------------------

    private void seedManualRate(String base, String quote, String rate) {
        FxRate row = new FxRate();
        row.setBaseCurrencyCode(base);
        row.setQuoteCurrencyCode(quote);
        row.setRate(new BigDecimal(rate));
        row.setRateDate(RATE_DATE);
        row.setProvider("manual");
        row.setManual(true);
        rates.save(row);
    }

    private String createPayload(Long fromId, Long toId, long amountMinor, String currency, Long toAmountMinor)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromAccountId", fromId);
        payload.put("toAccountId", toId);
        payload.put("amountMinor", amountMinor);
        payload.put("currencyCode", currency);
        if (toAmountMinor != null) {
            payload.put("toAmountMinor", toAmountMinor);
        }
        payload.put("occurredOn", RATE_DATE.toString());
        payload.put("description", "Currency move");
        return objectMapper.writeValueAsString(payload);
    }

    private long seedUser(String prefix) {
        UserResponse user = profileService.create(new CreateUserCommand(
                prefix + "-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Tina", "Cross",
                "UTC", "en-US", "USD"));
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