package com.spendio.transactions.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Bulk recategorise (ARCHITECTURE.md E.6 POST /transactions/bulk). */
public record BulkCategoryRequest(
        @NotEmpty @Size(max = 500) List<Long> ids,
        @NotNull Long categoryId) {
}
