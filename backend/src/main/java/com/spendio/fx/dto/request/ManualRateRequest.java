package com.spendio.fx.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Manual rate override (ARCHITECTURE.md D.5): stored under the {@code manual} provider,
 * which the resolver always prefers over fetched rows for the same pair.
 */
public record ManualRateRequest(
        @NotNull @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO-4217 code")
        String baseCurrencyCode,
        @NotNull @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be an ISO-4217 code")
        String quoteCurrencyCode,
        @NotNull @Positive(message = "rate must be positive")
        BigDecimal rate,
        @NotNull LocalDate rateDate) {

    public ManualRateRequest {
        baseCurrencyCode = baseCurrencyCode == null ? null : baseCurrencyCode.trim().toUpperCase(java.util.Locale.ROOT);
        quoteCurrencyCode = quoteCurrencyCode == null ? null : quoteCurrencyCode.trim().toUpperCase(java.util.Locale.ROOT);
    }
}