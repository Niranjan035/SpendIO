package com.spendio.imports.dto.response;

import java.time.LocalDate;
import java.util.List;

/** A within-file or against-ledger duplicate group (ARCHITECTURE.md D.12 / E.12). */
public record DuplicateGroupResponse(
        String dedupeHash,
        List<DuplicateRow> rows,
        List<Long> existingTransactionIds) {

    public record DuplicateRow(
            Long id,
            int rowNumber,
            String status,
            LocalDate occurredOn,
            Long amountMinor,
            String currencyCode,
            String description) {
    }
}