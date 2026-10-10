package com.spendio.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/verify-email (ARCHITECTURE.md D.2). */
public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {}
