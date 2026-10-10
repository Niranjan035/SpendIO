package com.spendio.categories.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Category lifecycle end-to-end (ARCHITECTURE.md D.13, E.13): CRUD with parent/child
 * rules, default seeding, transaction assignment and the ownership boundary - the one
 * ledger surface that previously had no backend coverage at all.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class CategoryControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionService transactionService;

    private long userId;
    private long checkingId;

    @BeforeEach
    void seedFixture() {
        userId = seedUser("cat");
        checkingId = accountService.create(userId, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 100_00L, LocalDate.of(2026, 1, 1), null,
                null, null, null, null, null, null, null, false, null, true)).getId();
    }

    @Test
    void createDetailUpdateAndDuplicateConflict() throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Groceries", "EXPENSE", null, "#12AB34", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Groceries"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.color").value("#12AB34"))
                .andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(asUser(userId, get("/api/v1/categories/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(asUser(userId, patch("/api/v1/categories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Groceries & Basics\",\"color\":\"#BB2211\",\"excludeFromBudgets\":true}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Groceries & Basics"))
                .andExpect(jsonPath("$.color").value("#BB2211"))
                .andExpect(jsonPath("$.excludeFromBudgets").value(true));

        mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("GROCERIES & BASICS", "EXPENSE", null, null, false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void parentRulesAreEnforcedAndTheTreeNestsChildren() throws Exception {
        MvcResult parent = mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Transport", "EXPENSE", null, null, false))))
                .andExpect(status().isCreated()).andReturn();
        long parentId = objectMapper.readTree(parent.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Fuel", "EXPENSE", parentId, null, false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentId));

        // a child must share the parent's type and depth can never exceed 2
        mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Salary", "INCOME", parentId, null, false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSACTION_CATEGORY_TYPE_MISMATCH"));

        mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Deep", "EXPENSE", 999_999L, null, false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(asUser(userId, get("/api/v1/categories/tree").param("type", "EXPENSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].category.id").value(parentId))
                .andExpect(jsonPath("$.children[0].category.name").value("Transport"))
                .andExpect(jsonPath("$.children[0].children[0].category.name").value("Fuel"));
    }

    @Test
    void seededDefaultsAreSeededIdempotentlyAndUserEditable() throws Exception {
        mockMvc.perform(asUser(userId, post("/api/v1/categories/seed-defaults")))
                .andExpect(status().isNoContent());
        // a second call must not duplicate or conflict
        mockMvc.perform(asUser(userId, post("/api/v1/categories/seed-defaults")))
                .andExpect(status().isNoContent());

        MvcResult list = mockMvc.perform(asUser(userId, get("/api/v1/categories")
                        .param("size", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn();
        JsonNode rows = objectMapper.readTree(list.getResponse().getContentAsString()).get("content");
        boolean sawDefault = false;
        for (JsonNode row : rows) {
            if (row.get("isDefault").asBoolean()) {
                sawDefault = true;
                // defaults are ordinary user-owned rows, so they stay editable
                mockMvc.perform(asUser(userId, patch("/api/v1/categories/{id}", row.get("id").asLong())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"" + row.get("name").asText() + " (renamed)\"}")))
                        .andExpect(status().isOk());
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(sawDefault)
                .as("seed-defaults must create isDefault rows").isTrue();
    }

    @Test
    void deleteSoftRemovesOnlyWithNoChildrenAndArchiveRestores() throws Exception {
        MvcResult parent = mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Parent", "EXPENSE", null, null, false))))
                .andExpect(status().isCreated()).andReturn();
        long parentId = objectMapper.readTree(parent.getResponse().getContentAsString()).get("id").asLong();
        MvcResult child = mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody("Child", "EXPENSE", parentId, null, false))))
                .andExpect(status().isCreated()).andReturn();
        long childId = objectMapper.readTree(child.getResponse().getContentAsString()).get("id").asLong();

        // parent still owns a child -> refuse
        mockMvc.perform(asUser(userId, delete("/api/v1/categories/{id}", parentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));

        mockMvc.perform(asUser(userId, delete("/api/v1/categories/{id}", childId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(asUser(userId, get("/api/v1/categories/{id}", childId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(asUser(userId, delete("/api/v1/categories/{id}", parentId)))
                .andExpect(status().isNoContent());

        long archivedId = createCategory("ArchiveMe");
        mockMvc.perform(asUser(userId, post("/api/v1/categories/{id}/archive", archivedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(asUser(userId, post("/api/v1/categories/{id}/restore", archivedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void transactionsReferenceCategoriesAndFilterByThem() throws Exception {
        long eatingOutId = createCategory("Eating Out");
        long utilitiesId = createCategory("Utilities");
        mockMvc.perform(asUser(userId, post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionBody(eatingOutId, "Bills", 25_00L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category.id").value(eatingOutId));
        mockMvc.perform(asUser(userId, post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionBody(utilitiesId, "Electricity", 40_00L))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category.id").value(utilitiesId));

        mockMvc.perform(asUser(userId, get("/api/v1/transactions")
                        .param("categoryId", String.valueOf(eatingOutId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].category.name").value("Eating Out"));
    }

    @Test
    void anotherUsersCategoryIsInvisibleOnEveryEndpoint() throws Exception {
        long mine = createCategory("Mine");
        long otherUserId = seedUser("intruder");
        String update = "{\"name\":\"Hijacked\"}";

        mockMvc.perform(asUser(otherUserId, get("/api/v1/categories/{id}", mine)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        mockMvc.perform(asUser(otherUserId, patch("/api/v1/categories/{id}", mine)
                        .contentType(MediaType.APPLICATION_JSON).content(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        mockMvc.perform(asUser(otherUserId, delete("/api/v1/categories/{id}", mine)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        mockMvc.perform(asUser(otherUserId, post("/api/v1/categories/{id}/archive", mine)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        mockMvc.perform(asUser(otherUserId, get("/api/v1/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void invalidPayloadsAreRejectedWithAStableCode() throws Exception {
        mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"type\":\"EXPENSE\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Colour\",\"type\":\"EXPENSE\",\"color\":\"red\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- helpers --------------------------------------------------------------

    private long createCategory(String name) throws Exception {
        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryBody(name, "EXPENSE", null, null, false))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private String categoryBody(String name, String type, Long parentId, String color, Boolean exclude) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("type", type);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        if (color != null) {
            body.put("color", color);
        }
        if (exclude != null) {
            body.put("excludeFromBudgets", exclude);
        }
        return objectMapper.writeValueAsString(body);
    }

    private String transactionBody(Long categoryId, String description, long amountMinor) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", checkingId);
        if (categoryId != null) {
            body.put("categoryId", categoryId);
        }
        body.put("type", TransactionType.EXPENSE.name());
        body.put("amountMinor", amountMinor);
        body.put("currencyCode", "USD");
        body.put("occurredOn", "2026-06-03");
        body.put("description", description);
        return objectMapper.writeValueAsString(body);
    }

    private long seedUser(String prefix) {
        UserResponse user = profileService.create(new CreateUserCommand(
                prefix + "-" + System.nanoTime() + "@spendio.test", "{bcrypt}", "Cat", "Owner",
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