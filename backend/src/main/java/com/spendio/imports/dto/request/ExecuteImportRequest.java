package com.spendio.imports.dto.request;

import java.util.List;

/** POST /imports/{id}/execute body (ARCHITECTURE.md D.12 step 8). */
public record ExecuteImportRequest(
        List<Long> rowIds,
        Boolean allowPartialImport,
        Boolean dryRun) {
}