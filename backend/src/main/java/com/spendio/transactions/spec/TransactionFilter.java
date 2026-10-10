package com.spendio.transactions.spec;

import com.spendio.categories.domain.Category;
import com.spendio.sharedkernel.domain.enums.TransactionDirection;
import com.spendio.sharedkernel.domain.enums.TransactionStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import java.time.LocalDate;
import java.util.List;

/**
 * Search filter for {@code GET /transactions} (ARCHITECTURE.md E.6): every field is
 * optional and AND-ed. {@code baseCurrencyOnly} resolves against the caller's base
 * currency in the service, never in here.
 */
public record TransactionFilter(
        String q,
        List<Long> accountIds,
        List<Long> categoryIds,
        Category.Type categoryType,
        List<TransactionType> types,
        List<TransactionDirection> directions,
        List<TransactionStatus> statuses,
        LocalDate from,
        LocalDate to,
        Long minAmountMinor,
        Long maxAmountMinor,
        String currencyCode,
        Boolean baseCurrencyOnly,
        Boolean recurringOnly,
        Boolean importedOnly,
        Boolean hasNotes,
        Boolean hasReversal) {

    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }
}
