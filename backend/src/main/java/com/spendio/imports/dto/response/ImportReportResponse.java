package com.spendio.imports.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Execution report (ARCHITECTURE.md D.12 / E.12 GET /imports/{id}/report). */
public record ImportReportResponse(
        ImportSummaryResponse import_,
        List<TransactionLine> createdTransactions,
        List<Failure> failures) {

    public record TransactionLine(
            Long id,
            LocalDate occurredOn,
            Long amountMinor,
            String currencyCode,
            String description) {
    }

    public record Failure(
            int rowNumber,
            String reason,
            Map<String, Object> raw) {
    }
}