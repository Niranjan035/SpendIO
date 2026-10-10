package com.spendio.transactions.domain;

import com.spendio.accounts.domain.Account;
import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A ledger row (ARCHITECTURE.md D.8, C.3 #10): the sign lives in {@code direction},
 * never in {@code amount_minor}; {@code exchange_rate} (base->transaction currency) and
 * {@code base_amount_minor} are frozen at write time and never recomputed. DB CHECKs
 * enforce the type/direction/category invariants this class also validates on write.
 *
 * <p>The generated {@code signed_*} columns are deliberately not mapped - balance maths
 * reads them natively, JPA never writes them.
 */
@Entity
@Table(name = "transactions")
public class Transaction extends SoftDeletableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

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

    @Column(name = "fx_rate_id")
    private Long fxRateId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status = TransactionStatus.POSTED;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "merchant", length = 160)
    private String merchant;

    @Column(name = "payee", length = 160)
    private String payee;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "notes")
    private String notes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "transfer_group_id", length = 36)
    private String transferGroupId;

    @Column(name = "counterparty_transaction_id")
    private Long counterpartyTransactionId;

    @Column(name = "reversal_of_transaction_id")
    private Long reversalOfTransactionId;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Column(name = "recurring_transaction_id")
    private Long recurringTransactionId;

    @Column(name = "recurring_run_id")
    private Long recurringRunId;

    @Column(name = "csv_import_id")
    private Long csvImportId;

    @Column(name = "csv_import_row_id")
    private Long csvImportRowId;

    @Column(name = "dedupe_key", length = 64)
    private String dedupeKey;

    protected Transaction() {
    }

    public static Transaction create(Long userId, Account account, Category category, TransactionType type,
            TransactionDirection direction, long amountMinor, String currencyCode, BigDecimal exchangeRate,
            long baseAmountMinor, Long fxRateId, LocalDate occurredOn, TransactionStatus status,
            String description, String merchant, String payee, String notes, String dedupeKey) {
        Transaction tx = new Transaction();
        tx.userId = userId;
        tx.account = account;
        tx.category = category;
        tx.type = type;
        tx.direction = direction;
        tx.amountMinor = amountMinor;
        tx.currencyCode = currencyCode;
        tx.exchangeRate = exchangeRate;
        tx.baseAmountMinor = baseAmountMinor;
        tx.fxRateId = fxRateId;
        tx.occurredOn = occurredOn;
        tx.status = status;
        tx.description = description;
        tx.merchant = merchant;
        tx.payee = payee;
        tx.notes = notes;
        tx.dedupeKey = dedupeKey;
        return tx;
    }

    /** Applies a freshly frozen amount/rate triple - used by create and amount/currency edits. */
    public void applyFrozenAmount(long amountMinor, BigDecimal exchangeRate, long baseAmountMinor, Long fxRateId) {
        this.amountMinor = amountMinor;
        this.exchangeRate = exchangeRate;
        this.baseAmountMinor = baseAmountMinor;
        this.fxRateId = fxRateId;
    }

    /** Joins a transfer group and cross-links the counterparty leg (ARCHITECTURE.md D.8). */
    public void linkTransfer(String transferGroupId, Long counterpartyTransactionId) {
        this.transferGroupId = transferGroupId;
        this.counterpartyTransactionId = counterpartyTransactionId;
    }

    /** The original row of a reversal: marks it reversed with the recorded reason. */
    public void markReversed(Instant reversedAt, String reversalReason) {
        this.reversedAt = reversedAt;
        this.reversalReason = reversalReason;
    }

    /** The mirrored entry of a reversal: points at the original and carries the reason. */
    public void markAsReversalOf(Long originalTransactionId, String reversalReason) {
        this.reversalOfTransactionId = originalTransactionId;
        this.reversalReason = reversalReason;
    }

    /** Joins the recurring rule and run that produced this ledger row (ARCHITECTURE.md J). */
    public void linkRecurring(Long recurringTransactionId, Long recurringRunId) {
        this.recurringTransactionId = recurringTransactionId;
        this.recurringRunId = recurringRunId;
    }

    /** Unlinks the rule only: run history keeps pointing at this row after DELETE. */
    public void unlinkRecurringRule() {
        this.recurringTransactionId = null;
    }

    /** Joins a CSV import source row (ARCHITECTURE.md D.12). */
    public void linkCsvImport(Long importId, Long rowId) {
        this.csvImportId = importId;
        this.csvImportRowId = rowId;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public void setOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getUserId() {
        return userId;
    }

    public Account getAccount() {
        return account;
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

    public Long getFxRateId() {
        return fxRateId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getPayee() {
        return payee;
    }

    public String getNotes() {
        return notes;
    }

    public String getTransferGroupId() {
        return transferGroupId;
    }

    public Long getCounterpartyTransactionId() {
        return counterpartyTransactionId;
    }

    public Long getReversalOfTransactionId() {
        return reversalOfTransactionId;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public String getReversalReason() {
        return reversalReason;
    }

    public Long getRecurringTransactionId() {
        return recurringTransactionId;
    }

    public Long getRecurringRunId() {
        return recurringRunId;
    }

    public Long getCsvImportId() {
        return csvImportId;
    }

    public Long getCsvImportRowId() {
        return csvImportRowId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }
}
