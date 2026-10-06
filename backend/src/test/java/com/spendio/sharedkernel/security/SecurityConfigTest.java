package com.spendio.sharedkernel.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendio.sharedkernel.web.CorsConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public vs protected route rules of the stateless skeleton (ARCHITECTURE.md A.2, E.1):
 * everything under {@code /api/v1} needs a token, the auth entry points and GET
 * currencies are open, CSRF protects only refresh/logout via double submit.
 */
@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
@Import({SecurityConfig.class, CorsConfig.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        SecurityConfigTest.ProbeController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedRouteReturns401WithProblemDetailAndRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f]{32}")))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"))
                .andExpect(jsonPath("$.instance").value("/api/v1/private"));
    }

    @WithMockUser
    @Test
    void mockUserPassesAuthorization() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isOk())
                .andExpect(content().string("private-ok"));
    }

    @Test
    void publicRoutesAreOpenAndLoginIsCsrfExempt() throws Exception {
        mockMvc.perform(get("/api/v1/currencies"))
                .andExpect(status().isOk())
                .andExpect(content().string("currencies-ok"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.c\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("login-ok"));
    }

    @Test
    void actuatorRoutesAreNot401() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getIssuesXsrfCookieForTheSpa() throws Exception {
        mockMvc.perform(get("/api/v1/currencies"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN")));
    }

    @Test
    void refreshWithoutCsrfTokenIs403AndIssuesCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN")));
    }

    @Test
    void refreshWithDoubleSubmitHeaderPassesCsrf() throws Exception {
        MvcResult initial = mockMvc.perform(get("/api/v1/currencies"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = initial.getResponse().getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("XSRF-TOKEN", cookie.getValue()))
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(content().string("refresh-ok"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/private")
        String priv() {
            return "private-ok";
        }

        @GetMapping("/api/v1/currencies")
        String currencies() {
            return "currencies-ok";
        }

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "login-ok";
        }

        @PostMapping("/api/v1/auth/refresh")
        String refresh() {
            return "refresh-ok";
        }
    }
}
