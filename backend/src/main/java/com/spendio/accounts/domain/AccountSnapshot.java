package com.spendio.accounts.domain;

import com.spendio.sharedkernel.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * Daily balance rollup for net-worth history (ARCHITECTURE.md C.3 #11, Phase 13).
 *
 * <p>One row per account per day, written by {@code AccountSnapshotJob} overnight and
 * backfilled on demand by {@code AccountSnapshotService.ensureToday}. Net-worth charts
 * read this instead of re-summing the whole ledger; the table is rebuildable from
 * {@code transactions} at any time, so rows are safe to drop and recapture.
 */
@Entity
@Table(
        name = "account_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_snapshot", columnNames = {"account_id", "snapshot_date"}))
public class AccountSnapshot extends AuditableEntity {

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Plain tenant column: entities never cross module boundaries (B.3). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "balance_base_minor", nullable = false)
    private long balanceBaseMinor;

    @Column(name = "base_currency_code", nullable = false, length = 3)
    private String baseCurrencyCode;

    protected AccountSnapshot() {
    }

    public AccountSnapshot(Long accountId, Long userId, LocalDate snapshotDate,
            long balanceMinor, long balanceBaseMinor, String baseCurrencyCode) {
        this.accountId = accountId;
        this.userId = userId;
        this.snapshotDate = snapshotDate;
        this.balanceMinor = balanceMinor;
        this.balanceBaseMinor = balanceBaseMinor;
        this.baseCurrencyCode = baseCurrencyCode;
    }

    /** Re-captures today's balance when the account moved since the last write. */
    public void recapture(long newBalanceMinor, long newBalanceBaseMinor) {
        this.balanceMinor = newBalanceMinor;
        this.balanceBaseMinor = newBalanceBaseMinor;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public long getBalanceBaseMinor() {
        return balanceBaseMinor;
    }

    public String getBaseCurrencyCode() {
        return baseCurrencyCode;
    }
}
