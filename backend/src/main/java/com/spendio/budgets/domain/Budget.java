package com.spendio.budgets.domain;

import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A spending budget (ARCHITECTURE.md C.3 #14, D.10): a fixed period window with an
 * allowance. {@code amount_minor} is denominated in {@code currency_code};
 * {@code base_amount_minor} and {@code rollover_amount_minor} (also in the budget
 * currency) feed progress maths, which happens in the budget currency via
 * {@link com.spendio.transactions.support.LedgerFx}.
 *
 * <p>A {@code null} category marks an overall budget covering every expense category;
 * the service layer keeps at most one of those per overlapping period because MySQL
 * unique indexes treat NULLs as distinct.
 */
@Entity
@Table(name = "budgets")
public class Budget extends SoftDeletableEntity {

    public enum PeriodType { WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM }

    public enum Status { ACTIVE, ARCHIVED }

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "base_amount_minor", nullable = false)
    private long baseAmountMinor;

    @Column(name = "rollover_enabled", nullable = false)
    private boolean rolloverEnabled;

    @Column(name = "rollover_amount_minor", nullable = false)
    private long rolloverAmountMinor;

    @Column(name = "alert_threshold_pct", nullable = false)
    private short alertThresholdPct = 80;

    @Column(name = "alert_enabled", nullable = false)
    private boolean alertEnabled = true;

    @Column(name = "include_subcategories", nullable = false)
    private boolean includeSubcategories = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    protected Budget() {
    }

    public static Budget create(Long userId, String name, Category category, PeriodType periodType,
            LocalDate periodStart, LocalDate periodEnd, long amountMinor, String currencyCode,
            long baseAmountMinor, boolean rolloverEnabled, short alertThresholdPct, boolean alertEnabled,
            boolean includeSubcategories) {
        Budget budget = new Budget();
        budget.userId = userId;
        budget.name = name;
        budget.category = category;
        budget.periodType = periodType;
        budget.periodStart = periodStart;
        budget.periodEnd = periodEnd;
        budget.amountMinor = amountMinor;
        budget.currencyCode = currencyCode;
        budget.baseAmountMinor = baseAmountMinor;
        budget.rolloverEnabled = rolloverEnabled;
        budget.alertThresholdPct = alertThresholdPct;
        budget.alertEnabled = alertEnabled;
        budget.includeSubcategories = includeSubcategories;
        return budget;
    }

    /** Moves the window forward and swaps in the carried-over allowance (rollover job). */
    public void advancePeriod(LocalDate nextStart, LocalDate nextEnd, long nextRolloverMinor) {
        this.periodStart = nextStart;
        this.periodEnd = nextEnd;
        this.rolloverAmountMinor = nextRolloverMinor;
    }

    public void archive() {
        this.status = Status.ARCHIVED;
    }

    /** Re-activates an archived budget (distinct from {@code restore()}, which un-deletes). */
    public void activate() {
        this.status = Status.ACTIVE;
    }

    /** Allowance for the current window: base amount plus whatever rolled over. */
    public long allowanceMinor() {
        return amountMinor + rolloverAmountMinor;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public void setPeriodType(PeriodType periodType) {
        this.periodType = periodType;
    }

    public void setPeriod(LocalDate periodStart, LocalDate periodEnd) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public void setBaseAmountMinor(long baseAmountMinor) {
        this.baseAmountMinor = baseAmountMinor;
    }

    public void setRolloverEnabled(boolean rolloverEnabled) {
        this.rolloverEnabled = rolloverEnabled;
    }

    public void setAlertThresholdPct(short alertThresholdPct) {
        this.alertThresholdPct = alertThresholdPct;
    }

    public void setAlertEnabled(boolean alertEnabled) {
        this.alertEnabled = alertEnabled;
    }

    public void setIncludeSubcategories(boolean includeSubcategories) {
        this.includeSubcategories = includeSubcategories;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public Category getCategory() {
        return category;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public long getBaseAmountMinor() {
        return baseAmountMinor;
    }

    public boolean isRolloverEnabled() {
        return rolloverEnabled;
    }

    public long getRolloverAmountMinor() {
        return rolloverAmountMinor;
    }

    public short getAlertThresholdPct() {
        return alertThresholdPct;
    }

    public boolean isAlertEnabled() {
        return alertEnabled;
    }

    public boolean isIncludeSubcategories() {
        return includeSubcategories;
    }

    public Status getStatus() {
        return status;
    }
}
