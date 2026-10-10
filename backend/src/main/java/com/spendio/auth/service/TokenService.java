package com.spendio.auth.service;

import com.spendio.auth.domain.UserSession;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.sharedkernel.security.JwtService;
import com.spendio.users.dto.response.UserResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Issues the access token half of an authentication response (ARCHITECTURE.md D.2). */
@Service
public class TokenService {

    private final JwtService jwtService;

    public TokenService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public AuthResponse issueAuthResponse(UserResponse user, UserSession session) {
        String accessToken = jwtService.issueAccessToken(
                user.id(), user.email(), user.emailVerified(), UUID.fromString(session.getSessionUuid()));
        return AuthResponse.of(accessToken, jwtService.accessTokenTtlSeconds(), user);
    }
}
