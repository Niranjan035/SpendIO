package com.spendio.transactions.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.spendio.transactions.dto.request.CheckDuplicateRequest;
import com.spendio.transactions.dto.request.CreateTransactionRequest;
import com.spendio.transactions.dto.request.ReverseTransactionRequest;
import com.spendio.transactions.dto.request.UpdateTransactionRequest;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.service.TransactionService;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
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
 * Ledger endpoints end-to-end (ARCHITECTURE.md Phase 6, E.6): create with idempotent
 * replay, filtered search, summary, options, detail/patch/delete lifecycle, bulk
 * actions, CSV export headers and the duplicate probe.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class TransactionControllerTest extends AbstractIntegrationTest {

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
    private long accountId;
    private long foodId;
    private long lunchId;
    private long dinnerId;
    private long salaryId;

    @BeforeEach
    void seedFixture() {
        UserResponse user = profileService.create(new CreateUserCommand(
                "ledger@spendio.test", "{bcrypt}", "Lena", "Ledger", "UTC", "en-US", "USD"));
        userId = user.id();
        com.spendio.accounts.domain.Account account = accountService.create(userId, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null, null,
                null, false, null, true));
        accountId = account.getId();
        foodId = categoryService.create(userId,
                new CreateCategoryRequest("Food", "EXPENSE", null, null, null, false)).getId();
        long payId = categoryService.create(userId,
                new CreateCategoryRequest("Pay", "INCOME", null, null, null, false)).getId();

        lunchId = create(1_500L, foodId, TransactionType.EXPENSE, LocalDate.of(2026, 6, 1), "Lunch");
        dinnerId = create(2_500L, foodId, TransactionType.EXPENSE, LocalDate.of(2026, 6, 2), "Dinner");
        salaryId = create(10_000L, payId, TransactionType.INCOME, LocalDate.of(2026, 6, 3), "Salary");
    }

    @Test
    void createReturns201AndReplaysTheSameRowForAnIdempotencyKey() throws Exception {
        String payload = objectMapper.writeValueAsString(new CreateTransactionRequest(
                accountId, foodId, TransactionType.EXPENSE, null, 1_500L, "USD", null,
                LocalDate.of(2026, 6, 1), "Lunch", null, null, null, null, null));

        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/transactions")
                        .header("Idempotency-Key", "create-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountMinor").value(1_500))
                .andExpect(jsonPath("$.baseAmountMinor").value(1_500))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andReturn();
        long firstId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        MvcResult replayed = mockMvc.perform(asUser(userId, post("/api/v1/transactions")
                        .header("Idempotency-Key", "create-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        long replayId = objectMapper.readTree(replayed.getResponse().getContentAsString()).get("id").asLong();

        assertThat(replayId).isEqualTo(firstId);
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        String payload = objectMapper.writeValueAsString(new CreateTransactionRequest(
                null, foodId, TransactionType.EXPENSE, null, 1_500L, "USD", null,
                LocalDate.of(2026, 6, 1), "Lunch", null, null, null, null, null));

        mockMvc.perform(asUser(userId, post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void searchBindsFilterAndSortParameters() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/transactions").param("type", "EXPENSE")
                        .param("sort", "amount,asc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].amountMinor").value(1_500))
                .andExpect(jsonPath("$.content[1].amountMinor").value(2_500));

        mockMvc.perform(asUser(userId, get("/api/v1/transactions").param("q", "Salary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(salaryId));

        mockMvc.perform(asUser(userId, get("/api/v1/transactions")
                        .param("from", "2026-06-02").param("to", "2026-06-02")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Dinner"));

        mockMvc.perform(asUser(userId, get("/api/v1/transactions")
                        .param("accountId", String.valueOf(accountId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void detailPatchAndDeleteFollowTheLifecycle() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/transactions/{id}", lunchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lunchId))
                .andExpect(jsonPath("$.description").value("Lunch"))
                .andExpect(jsonPath("$.category.name").value("Food"));

        mockMvc.perform(asUser(userId, patch("/api/v1/transactions/{id}", lunchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionRequest(
                                null, null, null, null, null, "Brunch", null, 0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Brunch"))
                .andExpect(jsonPath("$.version").exists());

        mockMvc.perform(asUser(userId, patch("/api/v1/transactions/{id}", lunchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionRequest(
                                null, null, null, null, null, "Stale", null, 0)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(asUser(userId, delete("/api/v1/transactions/{id}", lunchId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(asUser(userId, get("/api/v1/transactions/{id}", lunchId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void summaryAndOptionsServeTheFilterPanel() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/transactions/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.incomeBaseMinor").value(10_000))
                .andExpect(jsonPath("$.expenseBaseMinor").value(4_000))
                .andExpect(jsonPath("$.netBaseMinor").value(6_000))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"));

        mockMvc.perform(asUser(userId, get("/api/v1/transactions/filters/options")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts").isNotEmpty())
                .andExpect(jsonPath("$.accounts[0].name").value("Checking"))
                .andExpect(jsonPath("$.currencies[0]").value("USD"));
    }

    @Test
    void exportSendsACsvAttachment() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/transactions/export")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"spendio-transactions-")))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).startsWith("id,occurredOn,type,");
                    assertThat(body).contains("Lunch").contains("Dinner").contains("Salary");
                    assertThat(body.split("\r\n")).hasSize(4);
                });
    }

    @Test
    void checkDuplicateReportsConfidence() throws Exception {
        String payload = objectMapper.writeValueAsString(new CheckDuplicateRequest(
                accountId, LocalDate.of(2026, 6, 1), 1_500L, "USD", "Lunch"));

        mockMvc.perform(asUser(userId, post("/api/v1/transactions/check-duplicate")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.matchedTransactionId").value(lunchId));
    }

    @Test
    void bulkRecategoriseAndBulkDeleteReportUpdatedCounts() throws Exception {
        long otherFood = categoryService.create(userId,
                new CreateCategoryRequest("Dining", "EXPENSE", null, null, null, false)).getId();

        mockMvc.perform(asUser(userId, post("/api/v1/transactions/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + lunchId + "," + dinnerId + "],\"categoryId\":" + otherFood + "}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        mockMvc.perform(asUser(userId, delete("/api/v1/transactions/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[" + lunchId + "," + dinnerId + "]}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        mockMvc.perform(asUser(userId, get("/api/v1/transactions/{id}", dinnerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void unknownDetailIs404WithTheLedgerCode() throws Exception {
        mockMvc.perform(asUser(userId, get("/api/v1/transactions/{id}", 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void reverseMirrorsTheRowAndMarksTheOriginal() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new ReverseTransactionRequest("wrong charge", LocalDate.of(2026, 6, 1)));

        MvcResult created = mockMvc.perform(asUser(userId, post("/api/v1/transactions/{id}/reverse", lunchId)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("INCOME"))
                .andExpect(jsonPath("$.direction").value("INFLOW"))
                .andExpect(jsonPath("$.amountMinor").value(1_500))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.reversalOfTransactionId").value(lunchId))
                .andExpect(jsonPath("$.reversedAt").doesNotExist())
                .andReturn();
        long mirrorId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();
        assertThat(mirrorId).isNotEqualTo(lunchId);

        mockMvc.perform(asUser(userId, get("/api/v1/transactions/{id}", lunchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversedAt").exists());

        mockMvc.perform(asUser(userId, get("/api/v1/transactions/{id}", mirrorId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalOfTransactionId").value(lunchId));

        mockMvc.perform(asUser(userId, post("/api/v1/transactions/{id}/reverse", lunchId)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_ALREADY_REVERSED"));

        mockMvc.perform(asUser(userId, post("/api/v1/transactions/{id}/reverse", mirrorId)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_REVERSIBLE"));

        mockMvc.perform(asUser(userId, get("/api/v1/transactions").param("hasReversal", "true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("\"id\":" + lunchId).contains("\"id\":" + mirrorId);
                });
    }

    @Test
    void reverseRejectsAnUnknownRow() throws Exception {
        String payload = objectMapper.writeValueAsString(
                new ReverseTransactionRequest("x", LocalDate.of(2026, 6, 1)));

        mockMvc.perform(asUser(userId, post("/api/v1/transactions/{id}/reverse", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    // --- helpers --------------------------------------------------------------

    private long create(long amountMinor, long categoryId, TransactionType type, LocalDate occurredOn,
            String description) {
        TransactionResponse response = transactionService.create(userId, new CreateTransactionRequest(
                accountId, categoryId, type, null, amountMinor, "USD", null, occurredOn, description,
                null, null, null, null, null), null);
        return response.id();
    }

    private static MockHttpServletRequestBuilder asUser(long userId, MockHttpServletRequestBuilder builder) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user-" + userId + "@spendio.test", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return builder.with(SecurityMockMvcRequestPostProcessors.authentication(authentication));
    }
}
