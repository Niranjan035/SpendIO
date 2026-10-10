package com.spendio.accounts.dto.request;

import com.spendio.sharedkernel.domain.enums.AccountType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Create-account payload (ARCHITECTURE.md D.6). Cross-field rule: when an opening
 * balance is supplied it must be dated, otherwise the opening ADJUSTMENT row would have
 * no place in the ledger series.
 */
public record CreateAccountRequest(
        @NotBlank(message = "name is required")
        @Size(max = 80, message = "name must be at most 80 characters")
        String name,

        @NotNull(message = "type is required")
        AccountType type,

        @NotBlank(message = "currencyCode is required")
        @Size(min = 3, max = 3, message = "currencyCode must be a 3-letter ISO code")
        String currencyCode,

        long openingBalanceMinor,

        @PastOrPresent(message = "openingBalanceDate must not be in the future")
        LocalDate openingBalanceDate,

        @Size(max = 80, message = "institution must be at most 80 characters")
        String institution,

        @Pattern(regexp = "^\\d{4}$", message = "last4 must be exactly 4 digits")
        String last4,

        @PositiveOrZero(message = "creditLimitMinor must not be negative")
        Long creditLimitMinor,

        @Min(value = 1, message = "billingDay must be between 1 and 31")
        @Max(value = 31, message = "billingDay must be between 1 and 31")
        Integer billingDay,

        @Min(value = 1, message = "dueDay must be between 1 and 31")
        @Max(value = 31, message = "dueDay must be between 1 and 31")
        Integer dueDay,

        @Size(max = 40, message = "icon must be at most 40 characters")
        String icon,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color must be a hex color like #12AB34")
        String color,

        @Size(max = 500, message = "notes must be at most 500 characters")
        String notes,

        boolean primary,

        @PastOrPresent(message = "openingTransactionDate must not be in the future")
        LocalDate openingTransactionDate,

        boolean createOpeningBalanceTransaction) {

    @AssertTrue(message = "openingBalanceDate is required when openingBalanceMinor is non-zero")
    public boolean isOpeningBalanceConsistent() {
        return openingBalanceMinor == 0 || openingBalanceDate != null;
    }
}