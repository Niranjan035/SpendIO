package com.spendio.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/reset-password (ARCHITECTURE.md D.2). */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token, @NotBlank @Size(max = 72) String newPassword) {}
