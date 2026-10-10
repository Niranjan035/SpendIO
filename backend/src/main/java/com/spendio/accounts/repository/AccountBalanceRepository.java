package com.spendio.accounts.repository;

import com.spendio.accounts.spec.AccountFilter;
import com.spendio.accounts.support.BalanceLine;
import com.spendio.accounts.support.DailyNet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Balance-facing access to the ledger (ARCHITECTURE.md D.6). This module owns no
 * Transaction entity - B.3 forbids an accounts -> transactions dependency and the
 * transactions module arrives in Phase 8 - so balances are computed natively over the
 * V6 ledger. The rules mirrored here are exactly the ones the transactions module will
 * later reimplement: only non-deleted, non-VOID, non-opening rows flow into balance
 * maths.
 */
public interface AccountBalanceRepository {

    List<BalanceLine> balanceLines(Long userId);

    BalanceLine balanceLine(Long userId, Long accountId);

    Map<Long, Long> transactionCounts(Long userId);

    long countTransactions(Long userId, Long accountId);

    List<Long> matchingIds(Long userId, AccountFilter filter);

    List<DailyNet> dailyNets(Long userId, Long accountId, LocalDate from, LocalDate to);

    long netBefore(Long userId, Long accountId, LocalDate date);

    void insertAdjustment(Long userId, Long accountId, String direction, long amountMinor, String currencyCode,
            long baseAmountMinor, LocalDate occurredOn, String description, String dedupeKey);

    int softDeleteTransactionsFor(Long userId, Long accountId);
}