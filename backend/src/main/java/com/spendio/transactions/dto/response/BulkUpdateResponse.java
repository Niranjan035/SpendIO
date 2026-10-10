package com.spendio.transactions.dto.response;

/** Result of a bulk action: how many rows were actually eligible and updated. */
public record BulkUpdateResponse(int updated) {
}
