package com.spendio.users.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Internal command for creating an account. Registration (Phase 2) maps its request onto
 * this after the password policy check; tests use it directly (ARCHITECTURE.md D.3).
 */
public record CreateUserCommand(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String passwordHash,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @NotBlank String timezone,
        @NotBlank String locale,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String baseCurrencyCode) {}
