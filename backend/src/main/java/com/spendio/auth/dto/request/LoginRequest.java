package com.spendio.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/login (ARCHITECTURE.md D.2). {@code rememberDevice} defaults to false. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password,
        Boolean rememberDevice) {}
