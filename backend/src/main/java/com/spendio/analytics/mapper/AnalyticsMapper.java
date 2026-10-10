package com.spendio.analytics.mapper;

import com.spendio.analytics.dto.response.CategoryBreakdownResponse;
import com.spendio.analytics.repository.AnalyticsQueryRepository.CategoryAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * Shaping helpers for the analytics responses (ARCHITECTURE.md D.14): percentage
 * maths with one shared rounding rule (HALF_UP, 1 decimal) and the
 * {@code CategoryAmount -> CategoryBreakdownResponse} mapping.
 */
@Component
public class AnalyticsMapper {

    /** {@code part} as a percentage of {@code total}; zero when the total is empty. */
    public BigDecimal sharePct(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    /** Percent change from {@code previous} to {@code current}; null when previous is 0. */
    public BigDecimal deltaPct(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf((current - previous) * 100.0 / previous)
                .setScale(1, RoundingMode.HALF_UP);
    }

    /** Savings rate = (income - expense) / income; null when there was no income. */
    public BigDecimal savingsRatePct(long income, long expense) {
        if (income <= 0) {
            return null;
        }
        return BigDecimal.valueOf((income - expense) * 100.0 / income)
                .setScale(1, RoundingMode.HALF_UP);
    }

    public CategoryBreakdownResponse toCategoryBreakdown(CategoryAmount row, long totalAmount) {
        String name = row.name() == null || row.name().isBlank()
                ? CategoryBreakdownResponse.UNCATEGORIZED
                : row.name();
        String color = row.color() == null || row.color().isBlank()
                ? CategoryBreakdownResponse.DEFAULT_COLOR
                : row.color();
        return new CategoryBreakdownResponse(
                row.categoryId(),
                name,
                color,
                row.amountMinor(),
                sharePct(row.amountMinor(), totalAmount),
                row.transactionCount());
    }
}
