package com.spendio.users.dto.response;

import com.spendio.users.domain.User;
import java.time.Instant;

/** The profile as the rest of the app sees it (ARCHITECTURE.md D.3). */
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String timezone,
        String locale,
        String baseCurrencyCode,
        boolean emailVerified,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getTimezone(),
                user.getLocale(),
                user.getDefaultCurrencyCode(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
