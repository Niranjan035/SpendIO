package com.spendio.users.mapper;

import com.spendio.users.domain.UserPreference;
import com.spendio.users.dto.request.UpdatePreferencesRequest;
import com.spendio.users.dto.response.UserPreferenceResponse;

/** Maps preference DTOs onto a detached entity so a save replaces the row wholesale. */
public final class UserPreferenceMapper {

    private UserPreferenceMapper() {
    }

    public static UserPreferenceResponse toResponse(UserPreference preference) {
        return new UserPreferenceResponse(
                preference.getTheme(),
                preference.getDateFormat(),
                preference.getFirstDayOfWeek(),
                preference.getDecimalStyle(),
                preference.getGroupSeparator(),
                preference.getCurrencySymbolPosition(),
                preference.getNumberFormat(),
                preference.getBudgetAlertThresholdPct(),
                preference.getGoalReminderDays(),
                preference.getRecurringReminderDays(),
                preference.isEmailDigestEnabled(),
                preference.isReducedMotion());
    }

    public static UserPreference toEntity(UserPreference target, UpdatePreferencesRequest request) {
        target.setTheme(request.theme());
        target.setDateFormat(request.dateFormat().trim());
        target.setFirstDayOfWeek(request.firstDayOfWeek());
        target.setDecimalStyle(request.decimalStyle());
        target.setGroupSeparator(request.groupSeparator().trim());
        target.setCurrencySymbolPosition(request.currencySymbolPosition());
        target.setNumberFormat(request.numberFormat().trim());
        target.setBudgetAlertThresholdPct(request.budgetAlertThresholdPct());
        target.setGoalReminderDays(request.goalReminderDays());
        target.setRecurringReminderDays(request.recurringReminderDays());
        target.setEmailDigestEnabled(request.emailDigestEnabled());
        target.setReducedMotion(request.reducedMotion());
        return target;
    }
}
