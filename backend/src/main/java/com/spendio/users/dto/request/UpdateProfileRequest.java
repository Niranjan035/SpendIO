package com.spendio.users.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH /api/v1/users/me (ARCHITECTURE.md D.3). */
public record UpdateProfileRequest(
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Pattern(
                regexp = "^[A-Za-z0-9_+\\-]+(/[A-Za-z0-9_+\\-]+)*$",
                message = "must be an IANA timezone id such as Europe/Lisbon or UTC")
        String timezone,
        @Pattern(regexp = "^[a-z]{2}(-[A-Za-z]{2,4})?$", message = "must be a locale such as en-GB")
        String locale) {

    public UpdateProfileRequest {
        firstName = trimToNull(firstName);
        lastName = trimToNull(lastName);
        if (timezone != null) {
            timezone = timezone.trim();
        }
        if (locale != null) {
            locale = locale.trim();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
