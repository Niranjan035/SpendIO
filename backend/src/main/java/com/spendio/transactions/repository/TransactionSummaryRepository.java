package com.spendio.transactions.repository;

import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.transactions.domain.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/**
 * Aggregate reads over the ledger (ARCHITECTURE.md E.6): money totals and filter
 * facets. Implemented with the criteria API in {@code support} because these roll up
 * generated columns and GROUP BYs that a derived query cannot express.
 */
public interface TransactionSummaryRepository {

    Summary summarize(Specification<Transaction> filter);

    FilterFacets facets(Long userId);

    /** Money sums exclude VOID and TRANSFER rows; {@code count} is every matching row. */
    record Summary(long count, long inflowBaseMinor, long outflowBaseMinor) {
    }

    /** Global (unfiltered) sidebar numbers for the search screen. */
    record FilterFacets(LocalDate minDate, LocalDate maxDate, List<String> currencies,
            Map<TransactionType, Long> typeCounts) {
    }
}
