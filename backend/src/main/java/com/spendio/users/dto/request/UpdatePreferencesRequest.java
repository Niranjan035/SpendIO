package com.spendio.users.dto.request;

import com.spendio.users.domain.DecimalStyle;
import com.spendio.users.domain.SymbolPosition;
import com.spendio.users.domain.Theme;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/v1/users/me/preferences - preferences are replaced wholesale so the client
 * never has to guess which fields a partial update left alone (ARCHITECTURE.md D.3).
 */
public record UpdatePreferencesRequest(
        @NotNull Theme theme,
        @NotBlank @Size(max = 20) String dateFormat,
        @Min(1) @Max(7) int firstDayOfWeek,
        @NotNull DecimalStyle decimalStyle,
        @NotBlank @Size(max = 4) String groupSeparator,
        @NotNull SymbolPosition currencySymbolPosition,
        @NotBlank @Size(max = 24) String numberFormat,
        @Min(1) @Max(100) int budgetAlertThresholdPct,
        @Min(0) int goalReminderDays,
        @Min(0) int recurringReminderDays,
        boolean emailDigestEnabled,
        boolean reducedMotion) {}
