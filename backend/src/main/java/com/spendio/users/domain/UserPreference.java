package com.spendio.users.domain;

import com.spendio.sharedkernel.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Per-user display and reminder preferences (ARCHITECTURE.md C.3 #2).
 *
 * <p>One row per account, created with the user and replaced wholesale by
 * {@code PUT /users/me/preferences}; the ranges are enforced both by the DDL CHECK
 * constraints and by {@code PreferenceService}.
 */
@Entity
@Table(
        name = "user_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_preferences_user", columnNames = "user_id"))
public class UserPreference extends AuditableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", length = 16, nullable = false)
    private Theme theme = Theme.SYSTEM;

    @Column(name = "date_format", length = 20, nullable = false)
    private String dateFormat = "yyyy-MM-dd";

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "first_day_of_week", nullable = false)
    private int firstDayOfWeek = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "decimal_style", length = 8, nullable = false)
    private DecimalStyle decimalStyle = DecimalStyle.DOT;

    @Column(name = "group_separator", length = 4, nullable = false)
    private String groupSeparator = ",";

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_symbol_position", length = 8, nullable = false)
    private SymbolPosition currencySymbolPosition = SymbolPosition.BEFORE;

    @Column(name = "number_format", length = 24, nullable = false)
    private String numberFormat = "#,##0.00";

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "budget_alert_threshold_pct", nullable = false)
    private int budgetAlertThresholdPct = 80;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "goal_reminder_days", nullable = false)
    private int goalReminderDays = 30;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "recurring_reminder_days", nullable = false)
    private int recurringReminderDays = 3;

    @Column(name = "email_digest_enabled", nullable = false)
    private boolean emailDigestEnabled;

    @Column(name = "reduced_motion", nullable = false)
    private boolean reducedMotion;

    protected UserPreference() {
    }

    /** Seeded defaults a new account starts with, including the notification reminder windows. */
    public static UserPreference defaultsFor(Long userId) {
        UserPreference preference = new UserPreference();
        preference.userId = userId;
        return preference;
    }

    public void replaceWith(UserPreference other) {
        this.theme = other.theme;
        this.dateFormat = other.dateFormat;
        this.firstDayOfWeek = other.firstDayOfWeek;
        this.decimalStyle = other.decimalStyle;
        this.groupSeparator = other.groupSeparator;
        this.currencySymbolPosition = other.currencySymbolPosition;
        this.numberFormat = other.numberFormat;
        this.budgetAlertThresholdPct = other.budgetAlertThresholdPct;
        this.goalReminderDays = other.goalReminderDays;
        this.recurringReminderDays = other.recurringReminderDays;
        this.emailDigestEnabled = other.emailDigestEnabled;
        this.reducedMotion = other.reducedMotion;
    }

    public Long getUserId() {
        return userId;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public int getFirstDayOfWeek() {
        return firstDayOfWeek;
    }

    public void setFirstDayOfWeek(int firstDayOfWeek) {
        this.firstDayOfWeek = firstDayOfWeek;
    }

    public DecimalStyle getDecimalStyle() {
        return decimalStyle;
    }

    public void setDecimalStyle(DecimalStyle decimalStyle) {
        this.decimalStyle = decimalStyle;
    }

    public String getGroupSeparator() {
        return groupSeparator;
    }

    public void setGroupSeparator(String groupSeparator) {
        this.groupSeparator = groupSeparator;
    }

    public SymbolPosition getCurrencySymbolPosition() {
        return currencySymbolPosition;
    }

    public void setCurrencySymbolPosition(SymbolPosition currencySymbolPosition) {
        this.currencySymbolPosition = currencySymbolPosition;
    }

    public String getNumberFormat() {
        return numberFormat;
    }

    public void setNumberFormat(String numberFormat) {
        this.numberFormat = numberFormat;
    }

    public int getBudgetAlertThresholdPct() {
        return budgetAlertThresholdPct;
    }

    public void setBudgetAlertThresholdPct(int budgetAlertThresholdPct) {
        this.budgetAlertThresholdPct = budgetAlertThresholdPct;
    }

    public int getGoalReminderDays() {
        return goalReminderDays;
    }

    public void setGoalReminderDays(int goalReminderDays) {
        this.goalReminderDays = goalReminderDays;
    }

    public int getRecurringReminderDays() {
        return recurringReminderDays;
    }

    public void setRecurringReminderDays(int recurringReminderDays) {
        this.recurringReminderDays = recurringReminderDays;
    }

    public boolean isEmailDigestEnabled() {
        return emailDigestEnabled;
    }

    public void setEmailDigestEnabled(boolean emailDigestEnabled) {
        this.emailDigestEnabled = emailDigestEnabled;
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
    }
}
