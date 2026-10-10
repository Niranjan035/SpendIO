package com.spendio.analytics.repository;

import com.spendio.sharedkernel.domain.enums.AccountType;
import java.time.LocalDate;
import java.util.List;

/**
 * Native aggregate queries for the read-only analytics module (ARCHITECTURE.md D.14).
 *
 * <p>Every query is scoped to one user and reads the live ledger (non-deleted,
 * non-VOID, excluding {@code opening:} balance rows) or the {@code account_snapshots}
 * rollup. The implementation lives in {@code support} so the "repositories are
 * interfaces" ArchUnit rule holds - same trick as {@code AccountBalanceRepository}.
 */
public interface AnalyticsQueryRepository {

    /** Income/expense/count over a period, summed in base currency. */
    record Totals(long incomeMinor, long expenseMinor, long transactionCount) {
    }

    /** One calendar day of ledger activity (base currency). */
    record DailyFlow(LocalDate day, long incomeMinor, long expenseMinor) {
    }

    /** Per-category spend/income rollup; {@code categoryId} null = uncategorised. */
    record CategoryAmount(Long categoryId, String name, String color, long amountMinor, long transactionCount) {
    }

    /** One month of income/expense. */
    record MonthTotals(int year, int month, long incomeMinor, long expenseMinor) {
    }

    /** One account's snapshot on one day, in base currency. */
    record SnapshotPoint(LocalDate snapshotDate, Long accountId, String accountName,
            AccountType accountType, long balanceBaseMinor) {
    }

    /** Current balance inputs for one account (opening + signed ledger net). */
    record AccountLine(Long accountId, String name, AccountType type, String currencyCode,
            long openingBalanceMinor, long netMinor) {
    }

    Totals totals(Long userId, LocalDate from, LocalDate to);

    List<DailyFlow> dailyFlow(Long userId, LocalDate from, LocalDate to);

    /**
     * Category rollup ordered by amount descending.
     *
     * @param txType     {@code INCOME} / {@code EXPENSE}, or null for both
     * @param parentOnly aggregate child categories into their parent bucket
     */
    List<CategoryAmount> byCategory(Long userId, LocalDate from, LocalDate to, String txType,
            int limit, boolean parentOnly);

    List<MonthTotals> monthlyTotals(Long userId, LocalDate from, LocalDate to);

    List<SnapshotPoint> snapshots(Long userId, LocalDate from, LocalDate to);

    List<AccountLine> accountLines(Long userId);
}
