package com.spendio.analytics.dto.response;

import java.math.BigDecimal;

/**
 * Top-N spending categories with the period-over-period delta
 * (ARCHITECTURE.md D.14 {@code GET /analytics/top-spending}). {@code deltaPct} is
 * null when the category had no spend in the previous period.
 */
public record TopSpendingResponse(
        int rank,
        Long categoryId,
        String name,
        long amountMinor,
        BigDecimal sharePct,
        BigDecimal deltaPct) {
}
