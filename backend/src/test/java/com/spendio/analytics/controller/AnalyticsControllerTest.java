package com.spendio.analytics.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.categories.dto.request.CreateCategoryRequest;
import com.spendio.categories.service.CategoryService;
import com.spendio.goals.dto.request.CreateGoalRequest;
import com.spendio.goals.service.GoalService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Dashboard and analytics endpoints end-to-end against a real ledger
 * (ARCHITECTURE.md Phase 13 "Done when": dashboard figures reconcile with the
 * underlying ledger). Seeds October 2026 income/expense with a September baseline,
 * then asserts the summary, time series, breakdowns, rankings, utilisation, goal
 * progress, net-worth series and account breakdown, plus 401 and validation failures.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@AutoConfigureMockMvc
class AnalyticsControllerTest extends AbstractIntegrationTest {

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
    @Autowired
    private GoalService goalService;

    private long userId;
    private long checkingId;
    private long foodId;
    private long salaryId;

    @BeforeEach
    void seedFixture() {
        userId = seedUser();
        checkingId = accountService.create(userId, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 0, null,
                null, null, null, null, null, null, null, null, false, null, false)).getId();
        foodId = categoryService.create(userId,
                new CreateCategoryRequest("Food", "EXPENSE", null, null, "#AABB11", false)).getId();
        salaryId = categoryService.create(userId,
                new CreateCategoryRequest("Salary", "INCOME", null, null, null, false)).getId();

        transactionService.create(userId, new CreateTransactionRequest(
                checkingId, salaryId, TransactionType.INCOME, null, 500_00L, "USD", null,
                LocalDate.of(2026, 9, 15), "September pay", null, null, null, null, null), null);
        transactionService.create(userId, new CreateTransactionRequest(
                checkingId, foodId, TransactionType.EXPENSE, null, 100_00L, "USD", null,
                LocalDate.of(2026, 9, 20), "Lunch run", null, null, null, null, null), null);
        transactionService.create(userId, new CreateTransactionRequest(
                checkingId, foodId, TransactionType.EXPENSE, null, 200_00L, "USD", null,
                LocalDate.of(2026, 10, 3), "Groceries", null, null, null, null, null), null);
        transactionService.create(userId, new CreateTransactionRequest(
                checkingId, salaryId, TransactionType.INCOME, null, 300_00L, "USD", null,
                LocalDate.of(2026, 10, 5), "October pay", null, null, null, null, null), null);
    }

    @Test
    void dashboardSummaryReconcilesWithTheLedger() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/dashboard/summary").param("month", "2026-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalanceBaseMinor").value(500_00))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.incomeBaseMinor").value(300_00))
                .andExpect(jsonPath("$.expenseBaseMinor").value(200_00))
                .andExpect(jsonPath("$.savingsBaseMinor").value(100_00))
                .andExpect(jsonPath("$.savingsRatePct").value(33.3))
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.periodStart").value("2026-10-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-10-31"))
                .andExpect(jsonPath("$.compare.previousIncome").value(500_00))
                .andExpect(jsonPath("$.compare.previousExpense").value(100_00))
                .andExpect(jsonPath("$.compare.previousSavings").value(400_00))
                .andExpect(jsonPath("$.compare.deltas.incomePct").value(-40.0))
                .andExpect(jsonPath("$.compare.deltas.expensePct").value(100.0))
                .andExpect(jsonPath("$.compare.deltas.savingsPct").value(-75.0));
    }

    @Test
    void recentTransactionsReturnNewestFirstAndUpcomingIsEmpty() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/dashboard/recent-transactions").param("limit", "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].amountMinor").value(300_00))
                .andExpect(jsonPath("$[1].amountMinor").value(200_00))
                .andExpect(jsonPath("$[2].amountMinor").value(100_00));

        mockMvc.perform(asUser(userId, get("/api/v1/dashboard/upcoming-recurring").param("days", "14")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cashFlowBucketsTheWholeRangeIncludingEmptyMonths() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/analytics/cash-flow")
                        .param("from", "2026-09-01").param("to", "2026-11-30").param("granularity", "monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].period").value("2026-09"))
                .andExpect(jsonPath("$[0].incomeMinor").value(500_00))
                .andExpect(jsonPath("$[0].expenseMinor").value(100_00))
                .andExpect(jsonPath("$[0].netMinor").value(400_00))
                .andExpect(jsonPath("$[1].period").value("2026-10"))
                .andExpect(jsonPath("$[1].incomeMinor").value(300_00))
                .andExpect(jsonPath("$[1].expenseMinor").value(200_00))
                .andExpect(jsonPath("$[2].netMinor").value(0));
    }

    @Test
    void categoryBreakdownAndTopSpendingReportShares() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/analytics/category-breakdown")
                        .param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("type", "EXPENSE").param("limit", "50")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[0].amountMinor").value(200_00))
                .andExpect(jsonPath("$[0].sharePct").value(100.0))
                .andExpect(jsonPath("$[0].transactionCount").value(1));

        // Previous window (2026-08-31..2026-09-30) contains only the September spend,
        // so the delta against October is +100%.
        mockMvc.perform(asUser(userId, get("/api/v1/analytics/top-spending")
                        .param("from", "2026-10-01").param("to", "2026-10-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[0].amountMinor").value(200_00))
                .andExpect(jsonPath("$[0].deltaPct").value(100.0));
    }

    @Test
    void monthlyComparisonSetsNetAgainstThePreviousMonth() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/analytics/monthly-comparison").param("year", "2026")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[9].month").value("2026-10"))
                .andExpect(jsonPath("$[9].incomeMinor").value(300_00))
                .andExpect(jsonPath("$[9].expenseMinor").value(200_00))
                .andExpect(jsonPath("$[9].netMinor").value(100_00))
                .andExpect(jsonPath("$[9].vsPreviousMonthPct").value(-75.0));
    }

    @Test
    void budgetUtilizationReflectsLiveLedgerSpend() throws Exception {
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("name", "October total");
        budget.put("periodType", "MONTHLY");
        budget.put("periodStart", "2026-10-01");
        budget.put("periodEnd", "2026-10-31");
        budget.put("amountMinor", 1000_00);
        budget.put("currencyCode", "USD");
        budget.put("rolloverEnabled", false);
        budget.put("alertThresholdPct", 80);
        budget.put("alertEnabled", true);
        budget.put("includeSubcategories", true);

        mockMvc.perform(asUser(userId, post("/api/v1/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(budget))))
                .andExpect(status().isCreated());

        mockMvc.perform(asUser(userId, get("/api/v1/analytics/budget-utilization")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("October total"))
                .andExpect(jsonPath("$[0].spentMinor").value(200_00))
                .andExpect(jsonPath("$[0].amountMinor").value(1000_00))
                .andExpect(jsonPath("$[0].utilisationPct").value(20.0))
                .andExpect(jsonPath("$[0].remainingMinor").value(800_00))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"));
    }

    @Test
    void goalProgressListsActiveGoals() throws Exception {
        goalService.create(userId, new CreateGoalRequest(
                "Emergency fund", "for a rainy day", 1000_00L, "USD",
                LocalDate.of(2026, 12, 31), null, 1, null, null, null));

        mockMvc.perform(asUser(userId, get("/api/v1/analytics/goal-progress")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Emergency fund"))
                .andExpect(jsonPath("$[0].targetMinor").value(1000_00))
                .andExpect(jsonPath("$[0].currentMinor").value(0))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"));
    }

    @Test
    void netWorthSeriesEndsTodayFromSnapshots() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/analytics/net-worth")
                        .param("from", "2026-06-01").param("to", LocalDate.now().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].totalMinor").value(500_00))
                .andExpect(jsonPath("$[0].byAccount[0].balanceBaseMinor").value(500_00));
    }

    @Test
    void accountsBreakDownByAccountAndType() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/analytics/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBaseMinor").value(500_00))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.accounts.length()").value(1))
                .andExpect(jsonPath("$.accounts[0].accountId").value(checkingId))
                .andExpect(jsonPath("$.byType[0].totalBaseMinor").value(500_00));
    }

    @Test
    void incomeVsExpenseReportsASavingsRate() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/analytics/income-vs-expense")
                        .param("from", "2026-10-01").param("to", "2026-10-31").param("granularity", "monthly")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].savingsRatePct").value(33.3))
                .andExpect(jsonPath("$[0].savingsMinor").value(100_00));
    }

    @Test
    void unauthenticatedAndInvalidRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")).andExpect(status().isUnauthorized());

        mockMvc.perform(asUser(userId, get("/api/v1/dashboard/summary").param("month", "2026-99")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(asUser(userId, get("/api/v1/analytics/cash-flow").param("granularity", "hour")))
                .andExpect(status().isBadRequest());
    }

    // --- helpers --------------------------------------------------------------

    private long seedUser() {
        UserResponse user = profileService.create(new CreateUserCommand(
                "analytics-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Rita", "Analytics",
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