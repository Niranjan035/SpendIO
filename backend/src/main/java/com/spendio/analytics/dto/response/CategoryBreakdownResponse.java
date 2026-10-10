package com.spendio.analytics.dto.response;

import java.math.BigDecimal;

/**
 * One category's share of the period's total (ARCHITECTURE.md D.14
 * {@code GET /analytics/category-breakdown}); shares sum to 100 within rounding.
 * {@code categoryId} null and name {@code Uncategorized} = unclassified spend.
 */
public record CategoryBreakdownResponse(
        Long categoryId,
        String name,
        String color,
        long amountMinor,
        BigDecimal sharePct,
        long transactionCount) {

    public static final String UNCATEGORIZED = "Uncategorized";
    public static final String DEFAULT_COLOR = "#94A3B8";
}
