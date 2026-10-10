package com.spendio.accounts.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.dto.request.UpdateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
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
 * Account endpoints end-to-end (ARCHITECTURE.md Phase 3, E.5/E.6): CRUD, archive and
 * restore, primary flag, balances, adjust and the ledger write tied to opening balances.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class AccountControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AccountService accountService;
    @Autowired
    private ProfileService profileService;

    @Test
    void createReturns201WithTheComputedBalance() throws Exception {
        long userId = seedUser("ana@example.com");
        String payload = objectMapper.writeValueAsString(new CreateAccountRequest(
                "Main Checking", AccountType.BANK, "USD", 100_000_00, LocalDate.of(2026, 1, 1),
                "Acme Bank", "1234", null, null, null, "credit-card", "#1F6FEB", "main", true,
                null, true));

        mockMvc.perform(asUser(userId, post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Main Checking"))
                .andExpect(jsonPath("$.type").value("BANK"))
                .andExpect(jsonPath("$.primary").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.openingBalanceMinor").value(100_000_00))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.balanceMinor").value(100_000_00))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"));
    }

    @Test
    void createRejectsNonZeroOpeningWithoutADate() throws Exception {
        long userId = seedUser("ana@example.com");
        String payload = objectMapper.writeValueAsString(new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 1000, null,
                null, null, null, null, null, null, null, null, false, null, true));

        mockMvc.perform(asUser(userId, post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createRejectsDuplicateName() throws Exception {
        long userId = seedUser("ana@example.com");
        String payload = objectMapper.writeValueAsString(new CreateAccountRequest(
                "Duplicate", AccountType.BANK, "USD", 0, null,
                null, null, null, null, null, null, null, null, false, null, true));

        mockMvc.perform(asUser(userId, post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isCreated());

        mockMvc.perform(asUser(userId, post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NAME_DUPLICATE"));
    }

    @Test
    void listFiltersByTypeAndArchivedFlag() throws Exception {
        long userId = seedUser("ana@example.com");
        long bank = create(userId, "Checking", AccountType.BANK, false);
        create(userId, "Wallet", AccountType.WALLET, false);
        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/archive", bank)))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(userId, get("/api/v1/accounts")
                        .param("type", "BANK").param("includeArchived", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Checking"))
                .andExpect(jsonPath("$.content[0].status").value("ARCHIVED"));

        mockMvc.perform(asUser(userId, get("/api/v1/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        mockMvc.perform(asUser(userId, get("/api/v1/accounts").param("includeArchived", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void listFiltersByHasTransactions() throws Exception {
        long userId = seedUser("ana@example.com");
        long used = create(userId, "Spent", AccountType.BANK, false);
        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/adjust", used)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":2500,\"occurredOn\":\"2026-01-05\",\"reason\":\"correction\"}")))
                .andExpect(status().isOk());
        create(userId, "Empty", AccountType.BANK, false);

        mockMvc.perform(asUser(userId, get("/api/v1/accounts").param("hasTransactions", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Spent"));

        mockMvc.perform(asUser(userId, get("/api/v1/accounts").param("hasTransactions", "false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Empty"));
    }

    @Test
    void patchAppliesTheOptionalFields() throws Exception {
        long userId = seedUser("ana@example.com");
        long id = create(userId, "Card", AccountType.CREDIT_CARD, false);
        String payload = objectMapper.writeValueAsString(new UpdateAccountRequest(
                "Platinum", null, null, "New Bank", null, 5_000_00L, 5, 25,
                null, null, null));

        mockMvc.perform(asUser(userId, patch("/api/v1/accounts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platinum"))
                .andExpect(jsonPath("$.institution").value("New Bank"))
                .andExpect(jsonPath("$.creditLimitMinor").value(5_000_00))
                .andExpect(jsonPath("$.billingDay").value(5))
                .andExpect(jsonPath("$.dueDay").value(25));
    }

    @Test
    void patchRejectsCurrencyChangeOnceTransactionsExist() throws Exception {
        long userId = seedUser("ana@example.com");
        long id = create(userId, "Card", AccountType.CREDIT_CARD, false);
        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/adjust", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":-1000,\"occurredOn\":\"2026-02-01\",\"reason\":\"dispute\"}")))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(userId, patch("/api/v1/accounts/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currencyCode\":\"EUR\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CURRENCY_IMMUTABLE"));
    }

    @Test
    void adjustMovesTheBalanceAndReturnsTheNewReadOut() throws Exception {
        long userId = seedUser("ana@example.com");
        long id = create(userId, "Checking", AccountType.BANK, false);

        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/adjust", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":4200,\"occurredOn\":\"2026-01-05\",\"reason\":\"correction\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(4200))
                .andExpect(jsonPath("$.currencyCode").value("USD"));
    }

    @Test
    void adjustOnArchivedAccountIsRejected() throws Exception {
        long userId = seedUser("ana@example.com");
        long id = create(userId, "Checking", AccountType.BANK, false);
        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/archive", id)))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/adjust", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":100,\"occurredOn\":\"2026-03-01\",\"reason\":\"x\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ARCHIVED"));
    }

    @Test
    void archiveThenRestoreTogglesTheStatus() throws Exception {
        long userId = seedUser("ana@example.com");
        long id = create(userId, "Checking", AccountType.BANK, false);

        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/archive", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/restore", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteRefusesWhileTransactionsExistThenForceCleansUp() throws Exception {
        long userId = seedUser("ana@example.com");
        long id = create(userId, "Checking", AccountType.BANK, false);
        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/adjust", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":500,\"occurredOn\":\"2026-02-02\",\"reason\":\"testing\"}")))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(userId, delete("/api/v1/accounts/{id}", id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_HAS_TRANSACTIONS"));

        mockMvc.perform(asUser(userId, delete("/api/v1/accounts/{id}", id).param("force", "true")))
                .andExpect(status().isNoContent());
    }

    @Test
    void setPrimaryDemotesThePreviousPrimary() throws Exception {
        long userId = seedUser("ana@example.com");
        long first = create(userId, "First", AccountType.BANK, true);
        long second = create(userId, "Second", AccountType.BANK, false);

        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/set-primary", second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(true));

        mockMvc.perform(asUser(userId, get("/api/v1/accounts/{id}", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary").value(false));
    }

    @Test
    void summaryRollsUpBalances() throws Exception {
        long userId = seedUser("ana@example.com");
        create(userId, "Checking", AccountType.BANK, false);
        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/adjust", create(userId, "Savings", AccountType.BANK, false))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":100000,\"occurredOn\":\"2026-01-01\",\"reason\":\"opening\"}")))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(userId, get("/api/v1/accounts/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBaseMinor").value(1_000_00))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.byType.BANK").value(1_000_00))
                .andExpect(jsonPath("$.byCurrency.USD").value(1_000_00));
    }

    @Test
    void balanceSeriesCoversEveryDayInTheRange() throws Exception {
        long userId = seedUser("ana@example.com");
        long id = create(userId, "Checking", AccountType.BANK, false);
        mockMvc.perform(asUser(userId, post("/api/v1/accounts/{id}/adjust", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":900,\"occurredOn\":\"2026-01-01\",\"reason\":\"start\"}")))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(userId, get("/api/v1/accounts/{id}/balances", id)
                        .param("from", "2026-01-01").param("to", "2026-01-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].balanceMinor").value(900))
                .andExpect(jsonPath("$[1].balanceMinor").value(900))
                .andExpect(jsonPath("$[2].balanceMinor").value(900));
    }

    @Test
    void otherUsersAccountIsA404() throws Exception {
        long ana = seedUser("ana@example.com");
        long bruno = seedUser("bruno@example.com");
        long id = create(ana, "Checking", AccountType.BANK, false);

        mockMvc.perform(asUser(bruno, get("/api/v1/accounts/{id}", id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void anonymousCallIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    private long create(long userId, String name, AccountType type, boolean primary) {
        return accountService.create(userId, new CreateAccountRequest(
                name, type, "USD", 0, null, null, null, null, null, null, null, null, null,
                primary, null, true)).getId();
    }

    private long seedUser(String email) {
        UserResponse user = profileService.create(new CreateUserCommand(
                email, "{bcrypt}", "Ana", "Lopes", "America/New_York", "en-US", "USD"));
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