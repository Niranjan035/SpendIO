package com.spendio.sharedkernel.events;

import java.time.Instant;

/**
 * Published when a CSV import finishes execution (ARCHITECTURE.md D.12 step 8).
 *
 * <p>Carries the tallies so Phase 11's {@code ImportCompletionNotifier} can build a
 * summary without re-reading the import. Dedupe key {@code import:{importId}}
 * guarantees exactly one notification per executed import.
 */
public record ImportCompletedEvent(
        Long userId,
        Long importId,
        String originalFilename,
        int totalRows,
        int createdRows,
        int failedRows,
        Instant completedAt,
        String dedupeKey) {

    public static String dedupeKey(Long importId) {
        return "import:" + importId;
    }
}