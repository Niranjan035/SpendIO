package com.spendio.users.dto.response;

import com.spendio.users.domain.DecimalStyle;
import com.spendio.users.domain.SymbolPosition;
import com.spendio.users.domain.Theme;

/** Effective preferences the frontend applies immediately after a save (ARCHITECTURE.md D.3). */
public record UserPreferenceResponse(
        Theme theme,
        String dateFormat,
        int firstDayOfWeek,
        DecimalStyle decimalStyle,
        String groupSeparator,
        SymbolPosition currencySymbolPosition,
        String numberFormat,
        int budgetAlertThresholdPct,
        int goalReminderDays,
        int recurringReminderDays,
        boolean emailDigestEnabled,
        boolean reducedMotion) {}
