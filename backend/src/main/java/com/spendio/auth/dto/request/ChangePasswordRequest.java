package com.spendio.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/change-password (ARCHITECTURE.md D.2). */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword, @NotBlank @Size(max = 72) String newPassword) {}
