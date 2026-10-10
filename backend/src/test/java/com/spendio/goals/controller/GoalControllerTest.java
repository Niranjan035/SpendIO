package com.spendio.goals.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
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
 * Goal endpoints end-to-end (ARCHITECTURE.md Phase 10, E.11): create defaults, the
 * complete/reopen state machine, manual contributions flowing into the progress view,
 * summary roll-up, the PATCH guards (past target date, stale version) and ownership.
 * Transfer-linked contributions are covered by {@code GoalContributionServiceTest}
 * because they delegate to a real ledger transfer.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@AutoConfigureMockMvc
class GoalControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProfileService profileService;

    private long userId;

    @BeforeEach
    void seedFixture() {
        userId = seedUser("goals");
    }

    @Test
    void createReturns201WithDefaults() throws Exception {
        mockMvc.perform(asUser(userId, post("/api/v1/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPayload()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Emergency fund"))
                .andExpect(jsonPath("$.targetAmountMinor").value(1_000_000))
                .andExpect(jsonPath("$.currentAmountMinor").value(0))
                .andExpect(jsonPath("$.remainingMinor").value(1_000_000))
                .andExpect(jsonPath("$.progressPct").value(0))
                .andExpect(jsonPath("$.currencyCode").value("USD"))
                .andExpect(jsonPath("$.targetDate").value("2027-12-31"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.priority").value(5));
    }

    @Test
    void completeAndReopenStateMachine() throws Exception {
        long goalId = createAndGetGoalId();

        mockMvc.perform(asUser(userId, post("/api/v1/goals/{id}/complete", goalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(asUser(userId, post("/api/v1/goals/{id}/complete", goalId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOAL_ALREADY_COMPLETED"));

        mockMvc.perform(asUser(userId, post("/api/v1/goals/{id}/reopen", goalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void contributionsSummaryAndProgressReads() throws Exception {
        long goalId = createAndGetGoalId();

        mockMvc.perform(asUser(userId, post("/api/v1/goals/{id}/contributions", goalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contributionPayload(300_000)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountMinor").value(300_000))
                .andExpect(jsonPath("$.method").value("MANUAL"))
                .andExpect(jsonPath("$.transactionId").doesNotExist());

        mockMvc.perform(asUser(userId, get("/api/v1/goals/{id}/progress", goalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal.id").value(goalId))
                .andExpect(jsonPath("$.goal.currentAmountMinor").value(300_000))
                .andExpect(jsonPath("$.contributions.length()").value(1))
                .andExpect(jsonPath("$.chart").isArray())
                .andExpect(jsonPath("$.milestones[?(@.pct == 90)].crossed").value(false))
                .andExpect(jsonPath("$.milestones[?(@.pct == 100)].crossed").value(false));

        mockMvc.perform(asUser(userId, get("/api/v1/goals/{goalId}/contributions", goalId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(asUser(userId, get("/api/v1/goals/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCount").value(1))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.totalCurrentBaseMinor").value(300_000))
                .andExpect(jsonPath("$.totalTargetBaseMinor").value(1_000_000))
                .andExpect(jsonPath("$.overallProgressPct").value(30))
                .andExpect(jsonPath("$.goals.length()").value(1));
    }

    @Test
    void patchRejectsPastTargetDateAndStaleVersion() throws Exception {
        long goalId = createAndGetGoalId();

        mockMvc.perform(asUser(userId, patch("/api/v1/goals/{id}", goalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetDate\":\"2025-01-01\"}")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GOAL_TARGET_DATE_IN_PAST"));

        mockMvc.perform(asUser(userId, patch("/api/v1/goals/{id}", goalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":999}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void deleteAndOwnershipGuards() throws Exception {
        long goalId = createAndGetGoalId();
        long otherUserId = seedUser("intruder");

        mockMvc.perform(asUser(otherUserId, get("/api/v1/goals/{id}", goalId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GOAL_NOT_FOUND"));

        mockMvc.perform(asUser(otherUserId, patch("/api/v1/goals/{id}", goalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"nope\"}")))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(otherUserId, delete("/api/v1/goals/{id}", goalId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(otherUserId, get("/api/v1/goals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(userId, delete("/api/v1/goals/{id}", goalId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asUser(userId, get("/api/v1/goals/{id}", goalId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(asUser(userId, get("/api/v1/goals")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- helpers --------------------------------------------------------------

    private long createAndGetGoalId() throws Exception {
        return createGoal(createPayload());
    }

    private long createGoal(Map<String, Object> payload) throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private static Map<String, Object> createPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "Emergency fund");
        payload.put("description", "Six months of runway");
        payload.put("targetAmountMinor", 1_000_000L);
        payload.put("currencyCode", "USD");
        payload.put("targetDate", "2027-12-31");
        payload.put("priority", 5);
        payload.put("icon", "gift");
        payload.put("color", "#FF0000");
        payload.put("milestonePct", 90);
        return payload;
    }

    private static Map<String, Object> contributionPayload(long amountMinor) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("amountMinor", amountMinor);
        payload.put("contributedOn", "2026-10-01");
        payload.put("method", "MANUAL");
        return payload;
    }

    private long seedUser(String prefix) {
        UserResponse user = profileService.create(new CreateUserCommand(
                prefix + "-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Rita", "Goal",
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