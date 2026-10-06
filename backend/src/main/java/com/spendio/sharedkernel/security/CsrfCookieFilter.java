package com.spendio.sharedkernel.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures the {@code XSRF-TOKEN} cookie is issued before the response body so the SPA
 * can double-submit it (ARCHITECTURE.md E.1, decision: refresh/logout in cookies).
 *
 * <p>Two paths: on CSRF-protected routes {@link org.springframework.security.web.csrf.CsrfFilter}
 * has already exposed a {@link DeferredCsrfToken} attribute - materialising it here
 * (before the body is written) covers success and failure responses. On every other
 * request, when the cookie has not been seen yet, the token is seeded directly from the
 * repository so the very first safe read hands the cookie to the SPA.
 *
 * <p>Deliberately not a {@code @Component}: it is created inside the security filter
 * chain (only) so Boot does not also register it as a standalone servlet filter.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    static final String XSRF_COOKIE = "XSRF-TOKEN";

    private final CookieCsrfTokenRepository tokens;

    public CsrfCookieFilter(CookieCsrfTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!response.isCommitted()) {
            Object deferredToken = request.getAttribute(DeferredCsrfToken.class.getName());
            if (deferredToken instanceof DeferredCsrfToken token) {
                token.get();
            } else if (!hasXsrfCookie(request)) {
                tokens.saveToken(tokens.generateToken(request), request, response);
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean hasXsrfCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (XSRF_COOKIE.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
