package com.spendio.transactions.security;

import static org.hamcrest.Matchers.hasSize;
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
import com.spendio.transactions.dto.request.UpdateTransactionRequest;
import com.spendio.transactions.dto.response.TransactionResponse;
import com.spendio.transactions.service.TransactionService;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.service.ProfileService;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Cross-user isolation for the ledger (ARCHITECTURE.md B.3): user B must not read,
 * patch, delete or list user A's rows, and cannot post into A's account.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class TransactionSecurityTest extends AbstractIntegrationTest {

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

    private long alice;
    private long bob;
    private long aliceAccountId;
    private long aliceTxId;

    @BeforeEach
    void seedFixture() {
        alice = profileService.create(new CreateUserCommand(
                "alice-tx@spendio.test", "{bcrypt}", "Alice", "One", "UTC", "en-US", "USD")).id();
        bob = profileService.create(new CreateUserCommand(
                "bob-tx@spendio.test", "{bcrypt}", "Bob", "Two", "UTC", "en-US", "USD")).id();

        com.spendio.accounts.domain.Account account = accountService.create(alice, new CreateAccountRequest(
                "Alice Checking", AccountType.BANK, "USD", 0, null, null, null, null, null, null, null,
                null, null, false, null, true));
        aliceAccountId = account.getId();
        long food = categoryService.create(alice,
                new CreateCategoryRequest("Food", "EXPENSE", null, null, null, false)).getId();

        TransactionResponse tx = transactionService.create(alice, new CreateTransactionRequest(
                aliceAccountId, food, TransactionType.EXPENSE, null, 1_500L, "USD", null,
                LocalDate.of(2026, 6, 1), "Alice Lunch", null, null, null, null, null), null);
        aliceTxId = tx.id();
    }

    @Test
    void foreignDetailPatchAndDeleteAllAnswerNotFound() throws Exception {
        mockMvc.perform(asUser(bob, get("/api/v1/transactions/{id}", aliceTxId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        mockMvc.perform(asUser(bob, patch("/api/v1/transactions/{id}", aliceTxId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransactionRequest(
                                null, null, null, null, null, "Hijacked", null, 0)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        mockMvc.perform(asUser(bob, delete("/api/v1/transactions/{id}", aliceTxId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void foreignRowsNeverAppearInSearchOrSummary() throws Exception {
        mockMvc.perform(asUser(bob, get("/api/v1/transactions")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(asUser(bob, get("/api/v1/transactions")
                        .param("accountId", String.valueOf(aliceAccountId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(asUser(bob, get("/api/v1/transactions/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void createIntoAForeignAccountIsRejected() throws Exception {
        String payload = objectMapper.writeValueAsString(new CreateTransactionRequest(
                aliceAccountId, null, TransactionType.ADJUSTMENT,
                com.spendio.sharedkernel.domain.enums.TransactionDirection.INFLOW, 100L, "USD", null,
                LocalDate.of(2026, 6, 1), "Sneaky", null, null, null, null, null));

        mockMvc.perform(asUser(bob, post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(payload)))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder asUser(long userId, MockHttpServletRequestBuilder builder) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user-" + userId + "@spendio.test", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return builder.with(SecurityMockMvcRequestPostProcessors.authentication(authentication));
    }
}
