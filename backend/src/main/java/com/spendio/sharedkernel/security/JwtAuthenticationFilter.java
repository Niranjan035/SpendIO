package com.spendio.sharedkernel.security;

import com.spendio.sharedkernel.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a valid {@code Authorization: Bearer} access token into the request
 * {@link UserPrincipal} (ARCHITECTURE.md A.2, I.4).
 *
 * <p>Invalid or missing tokens simply continue unauthenticated - the security chain
 * answers protected routes with the ProblemDetail 401 from
 * {@link RestAuthenticationEntryPoint}. The failure reason is left as a request
 * attribute so the entry point can distinguish "expired" from "missing".
 *
 * <p>Deliberately not a {@code @Component}: it is constructed inside the security
 * filter chain so Boot does not also register it as a standalone servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute carrying the {@link ErrorCode} name for a rejected bearer token. */
    public static final String TOKEN_ERROR_ATTRIBUTE = "spendio.auth.tokenError";

    private static final List<GrantedAuthority> AUTHORITIES =
            List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_USER"));

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            authenticate(request, header.substring(7).trim());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        if (token.isEmpty()) {
            request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ErrorCode.AUTH_TOKEN_MISSING);
            return;
        }
        try {
            JwtService.AccessToken access = jwtService.parseAccessToken(token);
            UserPrincipal principal =
                    new UserPrincipal(access.userId(), access.email(), access.emailVerified(), access.sessionId());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, AUTHORITIES);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ErrorCode.AUTH_TOKEN_INVALID);
        }
    }
}
