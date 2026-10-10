package com.spendio.sharedkernel.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.spendio.sharedkernel.web.CorsConfig;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
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
@TestPropertySource(properties = "spendio.metrics.scrape-token=test-scrape-token")
class SecurityConfigTest {

    /** The chain builds the JWT filter from this bean; the slice never issues tokens. */
    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void rejectNonJwtBearerTokens() {
        // The slice never issues tokens; any bearer value (e.g. the Prometheus scrape
        // token) is treated like an invalid JWT, as JwtService would in production.
        when(jwtService.parseAccessToken(anyString())).thenThrow(new JwtException("not a jwt"));
    }

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
    void onlyHealthAndInfoActuatorEndpointsArePublic() throws Exception {
        // Approved public probes: permitted in the slice (404, never 401).
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
        // Operational endpoints are not public.
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void privatePrometheusScrapeRequiresTheConfiguredBearerToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
        // Correct shared token: permitted (404 in this slice - the point is "not 401").
        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-scrape-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsPreflightIsAllowedForConfiguredOriginOnly() throws Exception {
        mockMvc.perform(options("/api/v1/private")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/v1/private")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
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
    void logoutRequiresTheCsrfDoubleSubmitHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
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

        @PostMapping("/api/v1/auth/logout")
        String logout() {
            return "logout-ok";
        }

        @PostMapping("/api/v1/auth/refresh")
        String refresh() {
            return "refresh-ok";
        }
    }
}
