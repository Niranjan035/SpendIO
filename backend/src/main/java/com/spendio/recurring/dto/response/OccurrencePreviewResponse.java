package com.spendio.recurring.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Next-N occurrence preview for the UI (ARCHITECTURE.md D.9): each date carries the
 * frozen base estimate and whether the rule would actually run then.
 */
public record OccurrencePreviewResponse(List<Occurrence> occurrences) {

    public record Occurrence(LocalDate date, long estimatedAmountMinor, boolean willRun) {
    }
}
