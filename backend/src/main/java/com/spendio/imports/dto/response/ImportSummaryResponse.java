package com.spendio.imports.dto.response;

import com.spendio.sharedkernel.domain.enums.ImportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Job summary (ARCHITECTURE.md D.12 / E.12 GET /imports/{id}). */
public record ImportSummaryResponse(
        Long id,
        ImportStatus status,
        String originalFilename,
        long fileSizeBytes,
        String detectedDelimiter,
        String detectedEncoding,
        Integer headerRowNumber,
        List<String> columns,
        Map<String, Map<String, String>> mapping,
        Totals totals,
        String errorSummary,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt) {

    public record Fields(
            String dateColumn,
            String amountColumn,
            String descriptionColumn,
            String debitColumn,
            String creditColumn,
            String categoryColumn,
            String accountColumn,
            String currencyColumn,
            String typeColumn,
            String dateFormat,
            String amountFormat) {
    }

    public record Totals(
            int total,
            int valid,
            int invalid,
            int duplicate,
            int created,
            int skipped,
            int failed) {
    }
}