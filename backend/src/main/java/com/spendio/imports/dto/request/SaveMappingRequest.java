package com.spendio.imports.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/**
 * PUT /imports/{id}/mapping body (ARCHITECTURE.md D.12 step 4): the full
 * {@code csvColumn -> {field, formatHint}} table plus the canonical field pointers
 * and behaviour options.
 */
public record SaveMappingRequest(
        @NotEmpty Map<String, FieldMapping> columnMappings,
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
        String amountFormat,
        ImportOptions options) {

    /** A single csv column assignment: which field it feeds and an optional parse hint. */
    public record FieldMapping(String field, String formatHint) {
    }

    public record ImportOptions(
            String defaultCurrencyCode,
            String defaultDateFormat,
            Long targetAccountId,
            Boolean createMissingCategories,
            Boolean skipDuplicates,
            Boolean allowPartialImport,
            String defaultType) {
    }
}