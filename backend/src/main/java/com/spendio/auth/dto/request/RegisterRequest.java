package com.spendio.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /auth/register (ARCHITECTURE.md D.2). Optional fields fall back to defaults. */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 64) String timezone,
        @Size(max = 16) String locale,
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter ISO currency code") String baseCurrencyCode) {}
