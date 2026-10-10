package com.spendio.transactions.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Bulk soft delete (ARCHITECTURE.md E.6 DELETE /transactions/bulk). */
public record BulkDeleteRequest(
        @NotEmpty @Size(max = 500) List<Long> ids) {
}
