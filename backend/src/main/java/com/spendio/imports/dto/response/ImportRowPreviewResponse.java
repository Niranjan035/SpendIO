package com.spendio.imports.dto.response;

import com.spendio.sharedkernel.domain.enums.ImportRowStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** One row of the preview page (ARCHITECTURE.md D.12 / E.12). */
public record ImportRowPreviewResponse(
        Long id,
        int rowNumber,
        ImportRowStatus status,
        Map<String, Object> raw,
        Map<String, Object> parsed,
        LocalDate occurredOn,
        Long amountMinor,
        String currencyCode,
        String description,
        String type,
        Long accountId,
        Long categoryId,
        Long matchedAccountId,
        Long matchedCategoryId,
        String matchedBy,
        BigDecimal confidence,
        List<String> errors,
        Long duplicateOfRowId,
        Long duplicateOfTransactionId,
        Long createdTransactionId) {
}