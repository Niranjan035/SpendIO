package com.spendio.auth.dto.response;

import com.spendio.users.dto.response.UserResponse;
import java.util.List;

/**
 * Successful authentication payload (ARCHITECTURE.md D.2, E.2).
 *
 * <p>Superset of the frontend {@code SessionResponse}; the refresh token is never in
 * the body - it travels only in the httpOnly {@code spendio_rt} cookie (I.4).
 * {@code permissions} is empty in v1: the token carries identity, not authority (J.9).
 */
public record AuthResponse(String accessToken, long expiresIn, String tokenType, UserResponse user,
        List<String> permissions) {

    public static AuthResponse of(String accessToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, expiresIn, "Bearer", user, List.of());
    }
}
