package com.spendio.imports.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** PATCH /imports/{id}/rows/{rowId} body (ARCHITECTURE.md D.12 step 7). */
public record UpdateImportRowRequest(
        LocalDate occurredOn,
        @NotNull Long amountMinor,
        String currencyCode,
        String description,
        Long accountId,
        Long categoryId,
        String type,
        String notes) {
}