package com.spendio.imports.dto.request;

import java.util.Map;

/** Upload parameters (the file itself arrives as multipart), ARCHITECTURE.md E.12. */
public record CreateImportRequest(
        String defaultCurrencyCode,
        String defaultDateFormat,
        String delimiterOverride,
        String encodingOverride,
        Integer headerRowNumber,
        Long targetAccountId) {
}