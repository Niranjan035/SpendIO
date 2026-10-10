package com.spendio.users.dto.response;

import com.spendio.users.domain.User;
import java.time.Instant;

/**
 * Everything authentication needs to evaluate a login attempt, without the entity
 * leaving the module (ARCHITECTURE.md B.3, D.2).
 *
 * <p>{@code passwordHash} is only ever consumed by the auth module to verify
 * credentials; it never reaches a controller response.
 */
public record LoginStateResponse(
        UserResponse user,
        String passwordHash,
        boolean active,
        Instant lockedUntil,
        int failedLoginAttempts,
        boolean mustChangePassword) {

    public static LoginStateResponse from(User user) {
        return new LoginStateResponse(
                UserResponse.from(user),
                user.getPasswordHash(),
                user.isActive(),
                user.getLockedUntil(),
                user.getFailedLoginAttempts(),
                user.isMustChangePassword());
    }
}
