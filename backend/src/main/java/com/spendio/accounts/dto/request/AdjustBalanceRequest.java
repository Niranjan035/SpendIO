package com.spendio.accounts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Adjust-balance payload (ARCHITECTURE.md D.6). Writes a single ADJUSTMENT transaction
 * that moves the account to {@code newBalanceMinor}; the reason is audited on the row.
 */
public record AdjustBalanceRequest(
        long newBalanceMinor,
        LocalDate occurredOn,
        @NotBlank(message = "reason is required")
        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason) {
}