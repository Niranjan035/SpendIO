package com.spendio.budgets.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.service.CategoryService;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.service.TransactionService;
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

/**
 * Budget endpoints end-to-end (ARCHITECTURE.md Phase 9, E.10): create with the
 * canonical window, list/detail/current reads, live progress against real ledger
 * spend within the budget scope, the forced alert evaluation at both thresholds and
 * the ownership guards.
 *
 * <p>Deliberately not class-transactional: {@link BudgetAlertService#insertAlert}
 * inserts in REQUIRE_NEW, and a child inserting a row that FK-references a budget
 * created moments earlier blocks on the parent's uncommitted lock inside a
 * rollback-only test transaction. Without the enclosing transaction each request
 * commits normally, exactly as it would in production.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@AutoConfigureMockMvc
class BudgetControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private TransactionService transactionService;

    private long userId;
    private long checkingId;
    private long foodId;

    @BeforeEach
    void seedFixture() {
        userId = seedUser("budgets");
        checkingId = accountService.create(userId, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null, null,
                null, false, null, true)).getId();
        foodId = categoryService.create(userId,
                new CreateCategoryRequest("Food", "EXPENSE", null, null, null, false)).getId();
    }

    @Test
    void createReturns201WithCanonicalWindowAndDefaults() throws Exception {
        mockMvc.perform(asUser(userId, post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryPayload("October groceries", foodId, 100_000)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("October groceries"))
                .andExpect(jsonPath("$.periodType").value("MONTHLY"))
                .andExpect(jsonPath("$.periodStart").value("2026-10-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-10-31"))
                .andExpect(jsonPath("$.amountMinor").value(100_000))
                .andExpect(jsonPath("$.baseAmountMinor").value(100_000))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.rolloverEnabled").value(false))
                .andExpect(jsonPath("$.alertThresholdPct").value(80))
                .andExpect(jsonPath("$.includeSubcategories").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.category.id").value(foodId));
    }

    @Test
    void listDetailCurrentAndDeleteRoundTrip() throws Exception {
        long budgetId = createAndGetBudgetId(createPayload("October total"));

        mockMvc.perform(asUser(userId, get("/api/v1/budgets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(budgetId));

        mockMvc.perform(asUser(userId, get("/api/v1/budgets/{id}", budgetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(budgetId))
                .andExpect(jsonPath("$.periodType").value("MONTHLY"));

        mockMvc.perform(asUser(userId, get("/api/v1/budgets/current")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].budget.id").value(budgetId))
                .andExpect(jsonPath("$[0].spentMinor").value(0));

        mockMvc.perform(asUser(userId, delete("/api/v1/budgets/{id}", budgetId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asUser(userId, get("/api/v1/budgets/{id}", budgetId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUDGET_NOT_FOUND"));

        mockMvc.perform(asUser(userId, get("/api/v1/budgets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void progressReflectsLedgerSpendWithinTheScope() throws Exception {
        long budgetId = createAndGetBudgetId(categoryPayload("October food", foodId, 100_000));
        transactionService.create(userId, new CreateTransactionRequest(
                checkingId, foodId, TransactionType.EXPENSE, null, 50_000L, "USD", null,
                LocalDate.of(2026, 10, 2), "Lunch", null, null, null, null, null), null);

        mockMvc.perform(asUser(userId, get("/api/v1/budgets/{id}/progress", budgetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.id").value(budgetId))
                .andExpect(jsonPath("$.period.start").value("2026-10-01"))
                .andExpect(jsonPath("$.period.end").value("2026-10-31"))
                .andExpect(jsonPath("$.spentMinor").value(50_000))
                .andExpect(jsonPath("$.remainingMinor").value(50_000))
                .andExpect(jsonPath("$.overBudget").value(false))
                .andExpect(jsonPath("$.utilisationPct").isNumber())
                .andExpect(jsonPath("$.categoryBreakdown[0].categoryId").value(foodId))
                .andExpect(jsonPath("$.categoryBreakdown[0].spentMinor").value(50_000));
    }

    @Test
    void evaluateCreatesAlertsAtUserAndHardThresholds() throws Exception {
        long budgetId = createAndGetBudgetId(overallPayload("Over the top", 100_000));
        transactionService.create(userId, new CreateTransactionRequest(
                checkingId, foodId, TransactionType.EXPENSE, null, 120_000L, "USD", null,
                LocalDate.of(2026, 10, 5), "Rent", null, null, null, null, null), null);

        mockMvc.perform(asUser(userId, post("/api/v1/budgets/evaluate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2));

        mockMvc.perform(asUser(userId, post("/api/v1/budgets/evaluate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0));
    }

    @Test
    void anotherUsersBudgetIsInvisible() throws Exception {
        long budgetId = createAndGetBudgetId(createPayload("October total"));
        long otherUserId = seedUser("intruder");

        mockMvc.perform(asUser(otherUserId, get("/api/v1/budgets/{id}", budgetId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BUDGET_NOT_FOUND"));

        mockMvc.perform(asUser(otherUserId, patch("/api/v1/budgets/{id}", budgetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":1}")))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(otherUserId, delete("/api/v1/budgets/{id}", budgetId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(otherUserId, post("/api/v1/budgets/evaluate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0));

        mockMvc.perform(asUser(otherUserId, get("/api/v1/budgets")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- helpers --------------------------------------------------------------

    private long createAndGetBudgetId(Map<String, Object> payload) throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> createPayload(String name) {
        return overallPayload(name, 100_000);
    }

    private static Map<String, Object> overallPayload(String name, long amount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("periodType", "MONTHLY");
        payload.put("periodStart", "2026-10-01");
        payload.put("periodEnd", "2026-10-31");
        payload.put("amountMinor", amount);
        payload.put("currencyCode", "USD");
        payload.put("rolloverEnabled", false);
        payload.put("alertThresholdPct", 80);
        payload.put("alertEnabled", true);
        payload.put("includeSubcategories", true);
        return payload;
    }

    private static Map<String, Object> categoryPayload(String name, long categoryId, long amount) {
        Map<String, Object> payload = overallPayload(name, amount);
        payload.put("categoryId", categoryId);
        return payload;
    }

    private long seedUser(String prefix) {
        UserResponse user = profileService.create(new CreateUserCommand(
                prefix + "-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Rita", "Budget",
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