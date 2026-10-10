package com.spendio.recurring.domain;

import com.spendio.accounts.domain.Account;
import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import com.spendio.sharedkernel.domain.enums.RecurrenceFrequency;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A recurring schedule (ARCHITECTURE.md C.3 #12, D.9): a template for ledger rows plus
 * the state machine the scheduler drives. The frozen {@code exchange_rate}/
 * {@code base_amount_minor} pair is display-only - every execution re-freezes as of its
 * own {@code scheduled_for} date (J.6). Account/category links are the same lazy
 * associations {@link com.spendio.transactions.domain.Transaction} uses, so list reads
 * fetch-join them and the mapper never needs a cross-module service.
 */
@Entity
@Table(name = "recurring_transactions")
public class RecurringTransaction extends SoftDeletableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private TransactionDirection direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "exchange_rate", nullable = false, precision = 20, scale = 10)
    private BigDecimal exchangeRate;

    @Column(name = "base_amount_minor", nullable = false)
    private long baseAmountMinor;

    @Column(name = "description", length = 255)
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private RecurrenceFrequency frequency;

    @Column(name = "interval_count", nullable = false)
    private short intervalCount = 1;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "skip_weekends", nullable = false)
    private boolean skipWeekends;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_run_date", nullable = false)
    private LocalDate nextRunDate;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecurringStatus status = RecurringStatus.ACTIVE;

    @Column(name = "auto_post", nullable = false)
    private boolean autoPost = true;

    @Column(name = "max_retries", nullable = false)
    private short maxRetries = 3;

    @Column(name = "retry_count", nullable = false)
    private short retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "target_occurrence_date")
    private LocalDate targetOccurrenceDate;

    protected RecurringTransaction() {
    }

    public static RecurringTransaction create(Long userId, Account account, Account toAccount, Category category,
            TransactionType type, TransactionDirection direction, long amountMinor, String currencyCode,
            BigDecimal exchangeRate, long baseAmountMinor, String description, String notes,
            RecurrenceFrequency frequency, short intervalCount, Integer dayOfMonth, Integer dayOfWeek,
            boolean skipWeekends, LocalDate startDate, LocalDate endDate, LocalDate nextRunDate,
            boolean autoPost, short maxRetries, LocalDate targetOccurrenceDate) {
        RecurringTransaction rule = new RecurringTransaction();
        rule.userId = userId;
        rule.account = account;
        rule.toAccount = toAccount;
        rule.category = category;
        rule.type = type;
        rule.direction = direction;
        rule.amountMinor = amountMinor;
        rule.currencyCode = currencyCode;
        rule.exchangeRate = exchangeRate;
        rule.baseAmountMinor = baseAmountMinor;
        rule.description = description;
        rule.notes = notes;
        rule.frequency = frequency;
        rule.intervalCount = intervalCount;
        rule.dayOfMonth = dayOfMonth;
        rule.dayOfWeek = dayOfWeek;
        rule.skipWeekends = skipWeekends;
        rule.startDate = startDate;
        rule.endDate = endDate;
        rule.nextRunDate = nextRunDate;
        rule.autoPost = autoPost;
        rule.maxRetries = maxRetries;
        rule.targetOccurrenceDate = targetOccurrenceDate;
        return rule;
    }

    public void pause() {
        this.status = RecurringStatus.PAUSED;
    }

    public void resume() {
        this.status = RecurringStatus.ACTIVE;
        clearFailure();
    }

    public void cancel() {
        this.status = RecurringStatus.CANCELLED;
    }

    /** One failed attempt: counts the retry and, once the budget is spent, fails the rule. */
    public void markFailure(String error) {
        this.retryCount++;
        this.lastError = truncate(error, 500);
        if (retryCount >= maxRetries) {
            this.status = RecurringStatus.FAILED;
            this.failureReason = this.lastError;
        }
    }

    public void clearFailure() {
        this.retryCount = 0;
        this.lastError = null;
        this.failureReason = null;
    }

    /** Records a finished occurrence and moves the schedule to its next date (or COMPLETED). */
    public void advanceTo(LocalDate nextRunDate, LocalDate lastRunDate) {
        if (nextRunDate != null) {
            this.nextRunDate = nextRunDate;
        }
        this.lastRunDate = lastRunDate;
        this.generatedCount++;
        if (nextRunDate == null) {
            this.status = RecurringStatus.COMPLETED;
        }
    }

    /**
     * No occurrence remains (schedule edited past its end): terminal state without
     * touching {@code next_run_date}, which the schema keeps NOT NULL (C.3 #12).
     */
    public void markCompleted() {
        this.status = RecurringStatus.COMPLETED;
    }

    /**
     * Resume catch-up tallies its posted occurrences separately, because the loop must
     * not move {@code next_run_date} (the plan re-anchors it once, past every missed date).
     */
    public void recordGenerated(int count, LocalDate lastRunDate) {
        this.generatedCount += count;
        this.lastRunDate = lastRunDate;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public void setToAccount(Account toAccount) {
        this.toAccount = toAccount;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public void setDirection(TransactionDirection direction) {
        this.direction = direction;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public void applyFrozenAmount(long amountMinor, BigDecimal exchangeRate, long baseAmountMinor) {
        this.amountMinor = amountMinor;
        this.exchangeRate = exchangeRate;
        this.baseAmountMinor = baseAmountMinor;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setFrequency(RecurrenceFrequency frequency) {
        this.frequency = frequency;
    }

    public void setIntervalCount(short intervalCount) {
        this.intervalCount = intervalCount;
    }

    public void setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public void setSkipWeekends(boolean skipWeekends) {
        this.skipWeekends = skipWeekends;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public void setNextRunDate(LocalDate nextRunDate) {
        this.nextRunDate = nextRunDate;
    }

    public void setAutoPost(boolean autoPost) {
        this.autoPost = autoPost;
    }

    public void setMaxRetries(short maxRetries) {
        this.maxRetries = maxRetries;
    }

    public void setTargetOccurrenceDate(LocalDate targetOccurrenceDate) {
        this.targetOccurrenceDate = targetOccurrenceDate;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public Long getUserId() {
        return userId;
    }

    public Account getAccount() {
        return account;
    }

    public Account getToAccount() {
        return toAccount;
    }

    public Category getCategory() {
        return category;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public long getBaseAmountMinor() {
        return baseAmountMinor;
    }

    public String getDescription() {
        return description;
    }

    public String getNotes() {
        return notes;
    }

    public RecurrenceFrequency getFrequency() {
        return frequency;
    }

    public short getIntervalCount() {
        return intervalCount;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public boolean isSkipWeekends() {
        return skipWeekends;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    public LocalDate getLastRunDate() {
        return lastRunDate;
    }

    public RecurringStatus getStatus() {
        return status;
    }

    public boolean isAutoPost() {
        return autoPost;
    }

    public short getMaxRetries() {
        return maxRetries;
    }

    public short getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getGeneratedCount() {
        return generatedCount;
    }

    public LocalDate getTargetOccurrenceDate() {
        return targetOccurrenceDate;
    }
}
