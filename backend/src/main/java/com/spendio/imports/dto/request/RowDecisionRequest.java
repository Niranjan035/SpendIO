package com.spendio.imports.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** POST /imports/{id}/rows/decisions body (ARCHITECTURE.md D.12 step 7). */
public record RowDecisionRequest(
        @NotEmpty List<Long> rowIds,
        @NotNull String decision) {
}