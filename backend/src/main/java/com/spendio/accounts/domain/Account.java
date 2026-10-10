package com.spendio.accounts.domain;

import com.spendio.sharedkernel.domain.SoftDeletableEntity;
import com.spendio.sharedkernel.domain.enums.AccountStatus;
import com.spendio.sharedkernel.domain.enums.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "accounts")
public class Account extends SoftDeletableEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private AccountType type;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "opening_balance_minor", nullable = false)
    private long openingBalanceMinor;

    @Column(name = "opening_balance_date")
    private java.time.LocalDate openingBalanceDate;

    @Column(name = "institution", length = 80)
    private String institution;

    @Column(name = "last4", length = 4)
    private String last4;

    @Column(name = "credit_limit_minor")
    private Long creditLimitMinor;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "billing_day")
    private Integer billingDay;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "due_day")
    private Integer dueDay;

    @Column(name = "icon", length = 40)
    private String icon;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "archived_at")
    private java.time.Instant archivedAt;

    protected Account() {
    }

    public static Account create(Long userId, String name, AccountType type, String currencyCode,
            long openingBalanceMinor, java.time.LocalDate openingBalanceDate) {
        Account account = new Account();
        account.userId = userId;
        account.name = name;
        account.type = type;
        account.currencyCode = currencyCode;
        account.openingBalanceMinor = openingBalanceMinor;
        account.openingBalanceDate = openingBalanceDate;
        return account;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeType(AccountType type) {
        this.type = type;
    }

    public void changeCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public void setLast4(String last4) {
        this.last4 = last4;
    }

    public void setCreditLimitMinor(Long creditLimitMinor) {
        this.creditLimitMinor = creditLimitMinor;
    }

    public void setBillingDay(Integer billingDay) {
        this.billingDay = billingDay;
    }

    public void setDueDay(Integer dueDay) {
        this.dueDay = dueDay;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setPrimary(boolean primaryAccount) {
        this.primaryAccount = primaryAccount;
    }

    public boolean isPrimary() {
        return primaryAccount;
    }

    public void markArchived() {
        this.status = AccountStatus.ARCHIVED;
        this.archivedAt = java.time.Instant.now();
    }

    public void markRestored() {
        this.status = AccountStatus.ACTIVE;
        this.archivedAt = null;
    }

    public boolean isArchived() {
        return status == AccountStatus.ARCHIVED;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public long getOpeningBalanceMinor() {
        return openingBalanceMinor;
    }

    public java.time.LocalDate getOpeningBalanceDate() {
        return openingBalanceDate;
    }

    public String getInstitution() {
        return institution;
    }

    public String getLast4() {
        return last4;
    }

    public Long getCreditLimitMinor() {
        return creditLimitMinor;
    }

    public Integer getBillingDay() {
        return billingDay;
    }

    public Integer getDueDay() {
        return dueDay;
    }

    public String getIcon() {
        return icon;
    }

    public String getColor() {
        return color;
    }

    public String getNotes() {
        return notes;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public java.time.Instant getArchivedAt() {
        return archivedAt;
    }
}