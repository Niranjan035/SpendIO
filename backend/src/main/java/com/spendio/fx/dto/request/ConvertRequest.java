package com.spendio.fx.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Convert payload (ARCHITECTURE.md D.5): an amount already in integer minor units of
 * {@code fromCurrency}, priced as of {@code date}.
 */
public record ConvertRequest(
        @NotNull @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO-4217 code")
        String fromCurrency,
        @NotNull @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO-4217 code")
        String toCurrency,
        long amountMinor,
        @NotNull LocalDate date) {

    public ConvertRequest {
        fromCurrency = fromCurrency == null ? null : fromCurrency.trim().toUpperCase(java.util.Locale.ROOT);
        toCurrency = toCurrency == null ? null : toCurrency.trim().toUpperCase(java.util.Locale.ROOT);
    }
}