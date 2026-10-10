package com.spendio.sharedkernel.security;

import tools.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.spendio.config.SecurityProperties;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.error.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limiting in front of {@code /auth/**} (ARCHITECTURE.md D.16, K.1).
 *
 * <p>Fixed windows backed by Caffeine - no external dependency, evicted as soon as the
 * window passes. Two budgets: a tight one for the credential-guessing routes
 * (login, register, forgot-password, reset-password) and a loose one for everything
 * else under /auth (bootstrap refresh, session listing). Returns 429 with
 * {@code Retry-After} as RFC-9457 problem+json, straight from the shared handler.
 *
 * <p>Deliberately not a {@code @Component}: it is constructed inside the security
 * filter chain so Boot does not also register it as a standalone servlet filter.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> SENSITIVE_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password");

    private final SecurityProperties.RateLimit limits;
    private final Cache<String, Window> windows;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(SecurityProperties properties, ObjectMapper objectMapper) {
        this.limits = properties.rateLimit();
        this.objectMapper = objectMapper;
        this.windows = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(limits.window().plusMinutes(1))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int limit = SENSITIVE_PATHS.contains(request.getRequestURI())
                ? limits.sensitiveRequests()
                : limits.authRequests();
        String key = clientIp(request) + '|' + (SENSITIVE_PATHS.contains(request.getRequestURI()) ? 's' : 'a');

        Instant now = Instant.now();
        Window window = windows.get(key, ignored -> new Window(now));
        long count = window.increment(now, limits.window());
        if (count > limit) {
            long retryAfter = Math.max(1, window.secondsUntilReset(now, limits.window()));
            reject(request, response, retryAfter);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfter)
            throws IOException {
        var body = GlobalExceptionHandler.problemDetail(
                ErrorCode.RATE_LIMITED, "Too many requests; try again shortly", request);
        body.setProperty("retryAfterSeconds", retryAfter);
        response.setStatus(ErrorCode.RATE_LIMITED.statusCode());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma == -1 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private volatile Instant start;
        private long count;

        private Window(Instant start) {
            this.start = start;
        }

        private synchronized long increment(Instant now, Duration window) {
            if (now.minus(window).isAfter(start)) {
                start = now;
                count = 0;
            }
            return ++count;
        }

        private synchronized long secondsUntilReset(Instant now, Duration window) {
            Instant reset = start.plus(window);
            return Math.max(0, Duration.between(now, reset).getSeconds());
        }
    }
}
