package com.spendio.recurring.dto.response;

import java.util.List;

/**
 * {@code POST /recurring-transactions} result (ARCHITECTURE.md E.9): the saved rule plus
 * its first five occurrences. The wrapper is additive beyond D.9's field list, which
 * omitted the occurrences E.9 promises.
 */
public record CreateRecurringResponse(
        RecurringTransactionResponse rule,
        List<OccurrencePreviewResponse.Occurrence> occurrences) {
}
