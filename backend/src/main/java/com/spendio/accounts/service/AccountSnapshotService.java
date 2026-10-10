package com.spendio.accounts.service;

import com.spendio.accounts.domain.AccountSnapshot;
import com.spendio.accounts.repository.AccountSnapshotRepository;
import com.spendio.accounts.repository.AccountBalanceRepository;
import com.spendio.accounts.support.BalanceLine;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily net-worth rollup writer (ARCHITECTURE.md Phase 13, C.3 #11).
 *
 * <p>{@link #ensureToday(Long)} is the read-through path used by the dashboard and
 * analytics: when today's rows are missing (first load of the day, before the nightly
 * job ran) every account balance is captured once, so net-worth history always has a
 * point for today. The nightly {@code AccountSnapshotJob} backfills every user.
 */
@Service
public class AccountSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(AccountSnapshotService.class);

    private final AccountBalanceRepository ledger;
    private final AccountBalanceService balances;
    private final AccountSnapshotRepository snapshots;

    public AccountSnapshotService(AccountBalanceRepository ledger, AccountBalanceService balances,
            AccountSnapshotRepository snapshots) {
        this.ledger = ledger;
        this.balances = balances;
        this.snapshots = snapshots;
    }

    /** Captures today's balances for one user unless every account already has a row. */
    @Transactional
    public int ensureToday(Long userId) {
        List<BalanceLine> lines = ledger.balanceLines(userId);
        if (lines.isEmpty()) {
            return 0;
        }
        LocalDate today = LocalDate.now();
        if (snapshots.countByUserIdAndSnapshotDate(userId, today) >= lines.size()) {
            return 0;
        }
        String base = balances.baseCurrencyCode(userId);
        int written = 0;
        for (BalanceLine line : lines) {
            long balance = line.balance();
            long baseBalance = balances.toBase(userId, balance, line.currencyCode());
            AccountSnapshot existing = snapshots
                    .findByAccountIdAndSnapshotDate(line.accountId(), today)
                    .orElse(null);
            if (existing == null) {
                snapshots.save(new AccountSnapshot(
                        line.accountId(), userId, today, balance, baseBalance, base));
                written++;
            } else if (existing.getBalanceMinor() != balance
                    || existing.getBalanceBaseMinor() != baseBalance
                    || !existing.getBaseCurrencyCode().equals(base)) {
                existing.recapture(balance, baseBalance);
                snapshots.save(existing);
                written++;
            }
        }
        return written;
    }

    /** Nightly backfill for every user that still has accounts. */
    @Transactional
    public int captureAll() {
        int total = 0;
        for (Long userId : snapshots.findUserIdsWithActiveAccounts()) {
            try {
                total += ensureToday(userId);
            } catch (RuntimeException ex) {
                log.warn("snapshot capture failed for user {}: {}", userId, ex.getMessage());
            }
        }
        return total;
    }
}
