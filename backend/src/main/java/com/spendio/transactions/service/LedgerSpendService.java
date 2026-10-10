package com.spendio.transactions.service;

import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.transactions.repository.TransactionRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Category rollups over the ledger for downstream reporting modules
 * (ARCHITECTURE.md D.10 budget progress, B.3 module edges): budgets may depend on
 * this service but never on {@code TransactionRepository} directly.
 *
 * <p>Everything is returned in the user's <em>base</em> currency ({@code baseAmountMinor},
 * frozen at write time) so callers never need FX to aggregate; the spend definition is
 * deliberately narrow - POSTED, non-deleted income/expense rows only, which excludes
 * transfers, adjustments, void rows and soft-deleted rows in one place.
 */
@Service
public class LedgerSpendService {

    private final TransactionRepository transactions;

    public LedgerSpendService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    /** Summed {@code baseAmountMinor} per (category, type) for the inclusive date window. */
    @Transactional(readOnly = true)
    public List<CategorySpend> spendByCategory(Long userId, LocalDate from, LocalDate to) {
        return transactions
                .spendByCategory(userId, from, to, TransactionStatus.POSTED,
                        List.of(TransactionType.EXPENSE, TransactionType.INCOME))
                .stream()
                .map(row -> new CategorySpend((Long) row[0], (TransactionType) row[1],
                        row[2] == null ? 0L : ((Number) row[2]).longValue()))
                .toList();
    }

    /** One summed ledger row: the category it belongs to, its type, and the base-currency total. */
    public record CategorySpend(Long categoryId, TransactionType type, long baseAmountMinor) {
    }
}
