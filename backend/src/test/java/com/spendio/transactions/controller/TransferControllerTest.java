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
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
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
 * Transfer endpoints end-to-end (ARCHITECTURE.md Phase 7, E.8): linked pair creation
 * with exact balance preservation, group listing filtered by either leg, detail, and
 * atomic group reversal that restores both balances exactly once.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class TransferControllerTest extends AbstractIntegrationTest {

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

    private long userId;
    private long checkingId;
    private long savingsId;

    @BeforeEach
    void seedFixture() {
        userId = seedUser("xfer");
        checkingId = accountService.create(userId, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 500_00, LocalDate.of(2026, 1, 1), null, null, null,
                null, null, null, null, null, false, null, true)).getId();
        savingsId = accountService.create(userId, new CreateAccountRequest(
                "Savings", AccountType.BANK, "USD", 0, null, null, null, null,
                null, null, null, null, null, false, null, true)).getId();
    }

    @Test
    void createMovesBothBalancesExactly() throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(createPayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transferGroupId").isNotEmpty())
                .andExpect(jsonPath("$.fromTransaction.direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.toTransaction.direction").value("INFLOW"))
                .andExpect(jsonPath("$.fromTransaction.type").value("TRANSFER"))
                .andExpect(jsonPath("$.toTransaction.type").value("TRANSFER"))
                .andExpect(jsonPath("$.fromTransaction.amountMinor").value(20_000))
                .andExpect(jsonPath("$.toTransaction.amountMinor").value(20_000))
                .andExpect(jsonPath("$.fromTransaction.account.id").value(checkingId))
                .andExpect(jsonPath("$.toTransaction.account.id").value(savingsId))
                .andReturn();

        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(body.get("fromTransaction").get("transferGroupId").asText())
                .isEqualTo(body.get("transferGroupId").asText());
        assertThat(body.get("fromTransaction").get("id").asLong())
                .isNotEqualTo(body.get("toTransaction").get("id").asLong());

        assertThat(balanceService.current(userId, checkingId).balanceMinor()).isEqualTo(500_00 - 20_000);
        assertThat(balanceService.current(userId, savingsId).balanceMinor()).isEqualTo(20_000);
    }

    @Test
    void listPagesByGroupAndFiltersEitherLeg() throws Exception {
        String groupId = createAndGetGroup();

        mockMvc.perform(asUser(userId, get("/api/v1/transfers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transferGroupId").value(groupId))
                .andExpect(jsonPath("$.content[0].fromTransaction.direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.content[0].toTransaction.direction").value("INFLOW"));

        mockMvc.perform(asUser(userId, get("/api/v1/transfers")
                        .param("accountId", String.valueOf(savingsId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(asUser(userId, get("/api/v1/transfers")
                        .param("accountId", String.valueOf(checkingId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(asUser(userId, get("/api/v1/transfers")
                        .param("from", "2026-06-02").param("to", "2026-06-02")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(userId, get("/api/v1/transfers")
                        .param("from", "2026-06-01").param("to", "2026-06-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detailReturnsBothLegsAndUnknownGroupIs404() throws Exception {
        String groupId = createAndGetGroup();

        mockMvc.perform(asUser(userId, get("/api/v1/transfers/{id}", groupId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferGroupId").value(groupId))
                .andExpect(jsonPath("$.fromTransaction.id").isNumber())
                .andExpect(jsonPath("$.toTransaction.id").isNumber());

        mockMvc.perform(asUser(userId, get("/api/v1/transfers/{id}", "no-such-group")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void reverseRestoresBothBalancesAndIsSingleShot() throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(createPayload())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        String groupId = body.get("transferGroupId").asText();
        long fromLegId = body.get("fromTransaction").get("id").asLong();

        String reversePayload = objectMapper.writeValueAsString(
                Map.of("reason", "sent to the wrong account", "occurredOn", "2026-06-01"));

        mockMvc.perform(asUser(userId, post("/api/v1/transfers/{id}/reverse", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content(reversePayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferGroupId").value(groupId))
                .andExpect(jsonPath("$.fromTransaction.reversedAt").exists())
                .andExpect(jsonPath("$.toTransaction.reversedAt").exists());

        assertThat(balanceService.current(userId, checkingId).balanceMinor()).isEqualTo(500_00);
        assertThat(balanceService.current(userId, savingsId).balanceMinor()).isEqualTo(0);

        mockMvc.perform(asUser(userId, post("/api/v1/transfers/{id}/reverse", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content(reversePayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_ALREADY_REVERSED"));

        mockMvc.perform(asUser(userId, post("/api/v1/transactions/{id}/reverse", fromLegId)
                        .contentType(MediaType.APPLICATION_JSON).content(reversePayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_IMMUTABLE"));
    }

    @Test
    void validationAndMissingBodiesAreRejected() throws Exception {
        String sameAccount = objectMapper.writeValueAsString(Map.of(
                "fromAccountId", checkingId, "toAccountId", checkingId,
                "amountMinor", 1_000L, "currencyCode", "USD",
                "occurredOn", "2026-06-01"));
        mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(sameAccount)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String unknownAccount = objectMapper.writeValueAsString(Map.of(
                "fromAccountId", checkingId, "toAccountId", 999_999L,
                "amountMinor", 1_000L, "currencyCode", "USD",
                "occurredOn", "2026-06-01"));
        mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(unknownAccount)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"x\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void anotherUsersTransferIsInvisible() throws Exception {
        String groupId = createAndGetGroup();
        long otherUserId = seedUser("other");
        String reversePayload = objectMapper.writeValueAsString(
                Map.of("reason", "not mine", "occurredOn", "2026-06-01"));

        mockMvc.perform(asUser(otherUserId, get("/api/v1/transfers/{id}", groupId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        mockMvc.perform(asUser(otherUserId, post("/api/v1/transfers/{id}/reverse", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content(reversePayload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        mockMvc.perform(asUser(otherUserId, get("/api/v1/transfers")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- helpers --------------------------------------------------------------

    private String createPayload() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromAccountId", checkingId);
        payload.put("toAccountId", savingsId);
        payload.put("amountMinor", 20_000L);
        payload.put("currencyCode", "USD");
        payload.put("occurredOn", "2026-06-01");
        payload.put("description", "Rent split");
        return objectMapper.writeValueAsString(payload);
    }

    private String createAndGetGroup() throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON).content(createPayload())))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("transferGroupId").asText();
    }

    private long seedUser(String prefix) {
        UserResponse user = profileService.create(new CreateUserCommand(
                prefix + "-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Tina", "Transfer",
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
