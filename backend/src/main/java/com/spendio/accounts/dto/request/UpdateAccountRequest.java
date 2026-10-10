package com.spendio.accounts.dto.request;

import com.spendio.sharedkernel.domain.enums.AccountType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Update-account payload (ARCHITECTURE.md D.6). Everything is optional and only
 * non-null fields are applied. Currency and opening balance are immutable once set -
 * the currency becomes immutable the moment any transaction exists - and primary is
 * changed through the dedicated set-primary endpoint, so neither is offered here.
 */
public record UpdateAccountRequest(
        @Size(max = 80, message = "name must be at most 80 characters")
        String name,

        @Size(min = 3, max = 3, message = "currencyCode must be a 3-letter ISO code")
        String currencyCode,

        AccountType type,

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
        String notes) {
}