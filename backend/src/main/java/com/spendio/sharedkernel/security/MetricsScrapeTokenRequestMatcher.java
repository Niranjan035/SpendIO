package com.spendio.sharedkernel.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Permits only a {@code GET /actuator/prometheus} request that presents the configured
 * bearer scrape token (C2 production hardening).
 *
 * <p>Operational actuator endpoints are not public. The private Prometheus scrape keeps
 * working by presenting a shared token; when no token is configured the matcher never
 * matches, so the endpoint stays protected by authentication. The comparison is
 * constant-time to avoid leaking the token through timing.
 */
public final class MetricsScrapeTokenRequestMatcher implements RequestMatcher {

    private static final String SCRAPE_PATH = "/actuator/prometheus";
    private static final String BEARER_PREFIX = "Bearer ";

    private final byte[] expectedToken;

    public MetricsScrapeTokenRequestMatcher(String scrapeToken) {
        this.expectedToken =
                scrapeToken == null ? new byte[0] : scrapeToken.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        if (expectedToken.length == 0 || !"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        if (!SCRAPE_PATH.equals(request.getRequestURI())) {
            return false;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return false;
        }
        byte[] presented = header.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, presented);
    }
}
