package com.spendio.recurring.controller;

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
import com.spendio.recurring.domain.RecurringRun;
import com.spendio.recurring.repository.RecurringRunRepository;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.time.Instant;
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
 * Recurring endpoints end-to-end (ARCHITECTURE.md Phase 8, E.9): rule CRUD with the
 * five-occurrence preview, list filters, execution history, the pause/cancel state
 * machine and the run-now guards. The happy paths of run-now and resume deliberately
 * stay in {@code RecurringExecutionServiceTest} - they span REQUIRES_NEW transactions
 * that a rollback-only test transaction cannot see.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class RecurringControllerTest extends AbstractIntegrationTest {

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
    private RecurringRunRepository runs;

    private long userId;
    private long checkingId;
    private long foodId;

    @BeforeEach
    void seedFixture() {
        userId = seedUser("recurring");
        checkingId = accountService.create(userId, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null, null,
                null, false, null, true)).getId();
        foodId = categoryService.create(userId,
                new CreateCategoryRequest("Food", "EXPENSE", null, null, null, false)).getId();
    }

    // --- CRUD -----------------------------------------------------------------

    @Test
    void createReturns201WithAPreviewOfFiveOccurrences() throws Exception {
        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rule.id").isNumber())
                .andExpect(jsonPath("$.rule.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rule.direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.rule.type").value("EXPENSE"))
                .andExpect(jsonPath("$.rule.amountMinor").value(150_000))
                .andExpect(jsonPath("$.rule.baseAmountMinor").value(150_000))
                .andExpect(jsonPath("$.rule.nextRunDate").isNotEmpty())
                .andExpect(jsonPath("$.rule.exchangeRate").doesNotExist())
                .andExpect(jsonPath("$.occurrences.length()").value(5))
                .andExpect(jsonPath("$.occurrences[0].date").isNotEmpty())
                .andExpect(jsonPath("$.occurrences[0].willRun").value(true));
    }

    @Test
    void listFiltersByStatusTypeAccountAndDueWindow() throws Exception {
        long ruleId = createAndGetRuleId();

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ruleId));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("status", "PAUSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("type", "EXPENSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("type", "INCOME")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("accountId", String.valueOf(checkingId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("accountId", "999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("dueWithinDays", "60")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/pause", ruleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("status", "PAUSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")
                        .param("status", "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detailPreviewRunsAndUpcomingReadsWork() throws Exception {
        long ruleId = createAndGetRuleId();

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions/{id}", ruleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.account.id").value(checkingId))
                .andExpect(jsonPath("$.category.id").value(foodId));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions/{id}/preview", ruleId)
                        .param("count", "3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences.length()").value(3));

        RecurringRun run = RecurringRun.claim(ruleId, userId, LocalDate.now().minusDays(2));
        run.markSuccess(null, Instant.now());
        runs.save(run);

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions/{id}/runs", ruleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].attempts").value(1));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions/upcoming")
                        .param("days", "60")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void patchRevalidatesAndRejectsStaleVersions() throws Exception {
        long ruleId = createAndGetRuleId();

        mockMvc.perform(asUser(userId, patch("/api/v1/recurring-transactions/{id}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amountMinor", 200_000L, "description", "Updated gym")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.amountMinor").value(200_000))
                .andExpect(jsonPath("$.description").value("Updated gym"));

        mockMvc.perform(asUser(userId, patch("/api/v1/recurring-transactions/{id}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amountMinor", 500L, "version", 99)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(asUser(userId, patch("/api/v1/recurring-transactions/{id}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("frequency", "WEEKLY", "dayOfMonth", 5)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECURRING_FREQUENCY_FIELD_MISMATCH"));
    }

    @Test
    void deleteRemovesTheRuleFromEveryRead() throws Exception {
        long ruleId = createAndGetRuleId();

        mockMvc.perform(asUser(userId, delete("/api/v1/recurring-transactions/{id}", ruleId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions/{id}", ruleId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURRING_NOT_FOUND"));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(userId, delete("/api/v1/recurring-transactions/{id}", ruleId)))
                .andExpect(status().isNotFound());
    }

    // --- lifecycle ------------------------------------------------------------

    @Test
    void pauseAndCancelFollowTheStateMachineAndResumeRejectsActiveRules() throws Exception {
        long ruleId = createAndGetRuleId();

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/resume", ruleId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/pause", ruleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/pause", ruleId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECURRING_ALREADY_PAUSED"));

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/cancel", ruleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/cancel", ruleId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(asUser(userId, get("/api/v1/recurring-transactions/{id}", ruleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // --- run-now guards (execution itself is covered by RecurringExecutionServiceTest) ---

    @Test
    void runNowGuardsRejectUnknownFutureAndPausedRules() throws Exception {
        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/999999/run-now")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURRING_NOT_FOUND"));

        Map<String, Object> future = createPayload();
        future.put("startDate", LocalDate.now().plusDays(30).toString());
        future.put("description", "Future rule");
        long futureId = createRule(future);

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/run-now", futureId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RECURRING_NOT_DUE"));

        long pausedId = createAndGetRuleId();
        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/pause", pausedId)))
                .andExpect(status().isOk());
        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/{id}/run-now", pausedId)
                        .header("Idempotency-Key", "run-key-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions/999999/resume")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURRING_NOT_FOUND"));
    }

    // --- validation and ownership --------------------------------------------

    @Test
    void invalidCreatePayloadsAreRejected() throws Exception {
        Map<String, Object> missingAmount = createPayload();
        missingAmount.remove("amountMinor");
        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingAmount))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> wrongCurrency = createPayload();
        wrongCurrency.put("currencyCode", "EUR");
        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongCurrency))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Map<String, Object> categoryOnTransfer = createPayload();
        categoryOnTransfer.put("type", "TRANSFER");
        categoryOnTransfer.put("toAccountId", checkingId);
        mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoryOnTransfer))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void anotherUsersRuleIsInvisible() throws Exception {
        long ruleId = createAndGetRuleId();
        long otherUserId = seedUser("intruder");
        accountService.create(otherUserId, new CreateAccountRequest(
                "Other checking", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null,
                null, null, false, null, true));

        mockMvc.perform(asUser(otherUserId, get("/api/v1/recurring-transactions/{id}", ruleId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURRING_NOT_FOUND"));

        mockMvc.perform(asUser(otherUserId, patch("/api/v1/recurring-transactions/{id}", ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":1}")))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(otherUserId, post("/api/v1/recurring-transactions/{id}/pause", ruleId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(otherUserId, delete("/api/v1/recurring-transactions/{id}", ruleId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(otherUserId, get("/api/v1/recurring-transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- helpers --------------------------------------------------------------

    private long createAndGetRuleId() throws Exception {
        return createRule(createPayload());
    }

    private long createRule(Map<String, Object> payload) throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/recurring-transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("rule").get("id").asLong();
    }

    private Map<String, Object> createPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountId", checkingId);
        payload.put("categoryId", foodId);
        payload.put("type", "EXPENSE");
        payload.put("amountMinor", 150_000L);
        payload.put("currencyCode", "USD");
        payload.put("description", "Gym");
        payload.put("frequency", "MONTHLY");
        payload.put("intervalCount", 1);
        payload.put("dayOfMonth", 1);
        payload.put("startDate", "2026-01-01");
        return payload;
    }

    private long seedUser(String prefix) {
        UserResponse user = profileService.create(new CreateUserCommand(
                prefix + "-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Rita", "Recurring",
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
