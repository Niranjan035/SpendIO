package com.spendio.accounts.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendio.accounts.dto.request.CreateAccountRequest;
import com.spendio.accounts.service.AccountService;
import com.spendio.sharedkernel.domain.enums.AccountType;
import com.spendio.sharedkernel.security.UserPrincipal;
import com.spendio.support.AbstractIntegrationTest;
import com.spendio.users.dto.request.CreateUserCommand;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.ProfileService;
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
 * Cross-user isolation (ARCHITECTURE.md E.8): every account route scopes rows to the
 * authenticated user and answers 404 - never 403 - for anything outside it.
 */
@EnabledIf(value = "com.spendio.support.MySqlTestSupport#available",
        disabledReason = "no test database: set SPENDIO_TEST_DB_URL, start Docker, or run MySQL on port 3307")
@Transactional
@AutoConfigureMockMvc
class AccountSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AccountService accountService;
    @Autowired
    private ProfileService profileService;

    @Test
    void everyAccountRouteIsScopedToTheOwner() throws Exception {
        long owner = seedUser("ana@example.com");
        long stranger = seedUser("bruno@example.com");
        long id = accountService.create(owner, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 0, null, null, null,
                null, null, null, null, null, null, true, null, true)).getId();

        List<MockHttpServletRequestBuilder> attempts = List.of(
                get("/api/v1/accounts/{id}", id),
                get("/api/v1/accounts/{id}/balance", id),
                get("/api/v1/accounts/{id}/balances", id).param("from", "2026-01-01").param("to", "2026-01-05"),
                patch("/api/v1/accounts/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"),
                delete("/api/v1/accounts/{id}", id),
                post("/api/v1/accounts/{id}/archive", id),
                post("/api/v1/accounts/{id}/restore", id),
                post("/api/v1/accounts/{id}/set-primary", id),
                post("/api/v1/accounts/{id}/adjust", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newBalanceMinor\":1,\"occurredOn\":\"2026-01-02\",\"reason\":\"x\"}"));

        for (MockHttpServletRequestBuilder attempt : attempts) {
            mockMvc.perform(asUser(stranger, attempt))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        }
    }

    @Test
    void listIsFilteredToTheOwnerOnly() throws Exception {
        long owner = seedUser("ana@example.com");
        long stranger = seedUser("bruno@example.com");
        accountService.create(owner, new CreateAccountRequest(
                "Checking", AccountType.BANK, "USD", 0, null, null, null,
                null, null, null, null, null, null, true, null, true));

        mockMvc.perform(asUser(stranger, get("/api/v1/accounts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
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