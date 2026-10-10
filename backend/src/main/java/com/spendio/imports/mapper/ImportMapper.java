package com.spendio.imports.mapper;

import com.spendio.imports.domain.CsvImport;
import com.spendio.imports.domain.CsvImportRow;
import com.spendio.imports.dto.response.ColumnMappingResponse;
import com.spendio.imports.dto.response.DuplicateGroupResponse;
import com.spendio.imports.dto.response.ImportReportResponse;
import com.spendio.imports.dto.response.ImportRowPreviewResponse;
import com.spendio.imports.dto.response.ImportSummaryResponse;
import com.spendio.imports.dto.response.ImportSummaryResponse.Totals;
import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mapper from domain to import DTOs (ARCHITECTURE.md D.12). */
public final class ImportMapper {

    private ImportMapper() {
    }

    public static ImportSummaryResponse toSummary(CsvImport job) {
        Totals totals = new Totals(job.getTotalRows(), job.getValidRows(), job.getInvalidRows(),
                job.getDuplicateRows(), job.getCreatedRows(), job.getSkippedRows(), job.getFailedRows());
        return new ImportSummaryResponse(
                job.getId(),
                job.getStatus(),
                job.getOriginalFilename(),
                job.getFileSizeBytes(),
                job.getDetectedDelimiter(),
                job.getDetectedEncoding(),
                job.getHeaderRowNumber(),
                job.getFileHeaders() == null ? List.of() : job.getFileHeaders(),
                job.getMapping() == null || job.getMapping().getColumnMappings() == null
                        ? Map.of()
                        : job.getMapping().getColumnMappings(),
                totals,
                job.getErrorSummary(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedAt());
    }

    public static ImportRowPreviewResponse toRowPreview(CsvImportRow row) {
        Map<String, Object> parsed = row.getParsedJson() == null ? Map.of() : row.getParsedJson();
        Map<String, Object> raw = row.getRawJson() == null ? Map.of() : row.getRawJson();
        List<String> errors = row.getErrors() == null ? List.of() : row.getErrors();
        return new ImportRowPreviewResponse(
                row.getId(),
                row.getRowNumber(),
                row.getStatus(),
                raw,
                parsed,
                row.getOccurredOn(),
                row.getAmountMinor(),
                row.getCurrencyCode(),
                row.getDescription(),
                row.getType(),
                row.getAccountId(),
                row.getCategoryId(),
                row.getMatchedAccountId(),
                row.getMatchedCategoryId(),
                row.getMatchedBy(),
                row.getConfidence(),
                errors,
                row.getDuplicateOfRowId(),
                row.getDuplicateOfTransactionId(),
                row.getCreatedTransactionId());
    }

    public static ColumnMappingResponse toColumnMapping(List<String> headers,
            List<ColumnMappingResponse.Column> columns,
            List<ColumnMappingResponse.FieldPointer> mapping,
            List<String> unmappedRequired) {
        return new ColumnMappingResponse(columns, mapping, unmappedRequired);
    }

    public static DuplicateGroupResponse toDuplicateGroup(String dedupeHash,
            List<DuplicateGroupResponse.DuplicateRow> rows,
            List<Long> existingTransactionIds) {
        return new DuplicateGroupResponse(dedupeHash, rows, existingTransactionIds);
    }

    public static Map<String, Object> rawFromHeaders(List<String> headers, List<String> values) {
        Map<String, Object> raw = new LinkedHashMap<>();
        for (int i = 0; i < headers.size() && i < values.size(); i++) {
            raw.put(headers.get(i), values.get(i));
        }
        return raw;
    }

    public static ImportReportResponse toReport(ImportSummaryResponse summary,
            List<ImportReportResponse.TransactionLine> created,
            List<ImportReportResponse.Failure> failures) {
        return new ImportReportResponse(summary, created, failures);
    }
}