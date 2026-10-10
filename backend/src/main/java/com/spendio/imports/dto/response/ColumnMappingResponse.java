package com.spendio.imports.dto.response;

import java.util.List;

/** Suggested column mapping (ARCHITECTURE.md D.12 / E.12 GET /imports/{id}/columns). */
public record ColumnMappingResponse(
        List<Column> columns,
        List<FieldPointer> mapping,
        List<String> unmappedRequired) {

    public record Column(
            int index,
            String headerName,
            List<String> sampleValues,
            String suggestedField,
            double confidence) {
    }

    public record FieldPointer(String field, String column, String formatHint) {
    }
}