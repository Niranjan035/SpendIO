package com.spendio.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/forgot-password (ARCHITECTURE.md D.2). */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {}
