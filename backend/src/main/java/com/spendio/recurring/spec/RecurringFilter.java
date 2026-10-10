package com.spendio.recurring.spec;

import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import com.spendio.sharedkernel.domain.enums.TransactionType;

/**
 * Search filter for {@code GET /recurring-transactions} (ARCHITECTURE.md D.9, E.9):
 * every field is optional and AND-ed.
 */
public record RecurringFilter(
        RecurringStatus status,
        TransactionType type,
        Long accountId,
        Long categoryId,
        Integer dueWithinDays) {

    public static RecurringFilter none() {
        return new RecurringFilter(null, null, null, null, null);
    }
}
