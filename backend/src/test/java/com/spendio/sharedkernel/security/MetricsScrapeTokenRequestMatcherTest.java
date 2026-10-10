package com.spendio.sharedkernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Focused unit tests for the C2 Prometheus scrape-token matcher.
 */
class MetricsScrapeTokenRequestMatcherTest {

    private static final String TOKEN = "scrape-secret-token";

    private final MetricsScrapeTokenRequestMatcher matcher = new MetricsScrapeTokenRequestMatcher(TOKEN);

    @Test
    void matchesGetPrometheusWithTheExactBearerToken() {
        assertThat(matcher.matches(request("GET", "Bearer " + TOKEN))).isTrue();
        assertThat(matcher.matches(request("GET", "bearer " + TOKEN))).isTrue();
        assertThat(matcher.matches(request("GET", "Bearer " + TOKEN + " "))).isTrue();
    }

    @Test
    void rejectsMissingOrWrongCredentials() {
        assertThat(matcher.matches(request("GET", null))).isFalse();
        assertThat(matcher.matches(request("GET", "Bearer wrong"))).isFalse();
        assertThat(matcher.matches(request("GET", TOKEN))).isFalse();
        assertThat(matcher.matches(request("GET", "Basic " + TOKEN))).isFalse();
    }

    @Test
    void rejectsOtherMethodsAndPaths() {
        assertThat(matcher.matches(request("POST", "Bearer " + TOKEN))).isFalse();

        MockHttpServletRequest otherPath = new MockHttpServletRequest("GET", "/actuator/metrics");
        otherPath.addHeader("Authorization", "Bearer " + TOKEN);
        assertThat(matcher.matches(otherPath)).isFalse();
    }

    @Test
    void neverMatchesWhenNoTokenIsConfigured() {
        assertThat(new MetricsScrapeTokenRequestMatcher("").matches(request("GET", "Bearer " + TOKEN)))
                .isFalse();
        assertThat(new MetricsScrapeTokenRequestMatcher(null).matches(request("GET", "Bearer " + TOKEN)))
                .isFalse();
    }

    private static MockHttpServletRequest request(String method, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/actuator/prometheus");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
