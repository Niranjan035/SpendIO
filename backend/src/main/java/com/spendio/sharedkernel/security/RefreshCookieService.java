package com.spendio.sharedkernel.security;

import com.spendio.config.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * The httpOnly refresh-token cookie (ARCHITECTURE.md I.4, E.1).
 *
 * <p>Opaque token, {@code SameSite=Strict}, scoped to the auth endpoints so it never
 * travels with ordinary API calls; JS cannot read it.
 */
@Service
public class RefreshCookieService {

    private final SecurityProperties.Cookie cookieProperties;
    private final Duration refreshTokenTtl;

    public RefreshCookieService(SecurityProperties properties) {
        this.cookieProperties = properties.cookie();
        this.refreshTokenTtl = properties.jwt().refreshTokenTtl();
    }

    /** Raw refresh token from the request cookie, or {@code null} when absent. */
    public String readRawToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieProperties.name().equals(cookie.getName())) {
                String value = cookie.getValue();
                return value == null || value.isBlank() ? null : value;
            }
        }
        return null;
    }

    public void writeRawToken(HttpServletResponse response, String rawToken) {
        write(response, build(rawToken, refreshTokenTtl));
    }

    public void writeRawToken(HttpServletResponse response, String rawToken, Duration ttl) {
        write(response, build(rawToken, ttl));
    }

    public void clear(HttpServletResponse response) {
        write(response, build("", Duration.ZERO));
    }

    private ResponseCookie build(String rawToken, Duration ttl) {
        return ResponseCookie.from(cookieProperties.name(), rawToken)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .path(cookieProperties.path())
                .maxAge(ttl)
                .sameSite("Strict")
                .build();
    }

    private static void write(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
