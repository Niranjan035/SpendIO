package com.spendio.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PUT /api/v1/users/me/base-currency.
 *
 * <p>{@code revalueExistingTransactions} defaults to {@code false}: history keeps the rate it
 * was frozen at (ARCHITECTURE.md D.3, ADR-0005).
 */
public record ChangeBaseCurrencyRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO-4217 code")
        String currencyCode,
        boolean revalueExistingTransactions) {

    public ChangeBaseCurrencyRequest {
        currencyCode = currencyCode == null ? null : currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
