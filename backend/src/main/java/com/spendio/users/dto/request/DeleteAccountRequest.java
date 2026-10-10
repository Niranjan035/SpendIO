package com.spendio.users.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * DELETE /api/v1/users/me (ARCHITECTURE.md E.3, D.3): password confirmation plus a
 * typed acknowledgement so the destructive action is deliberate.
 */
public record DeleteAccountRequest(
        @jakarta.validation.constraints.NotBlank String password,
        @jakarta.validation.constraints.NotBlank String confirmationText) {
}
