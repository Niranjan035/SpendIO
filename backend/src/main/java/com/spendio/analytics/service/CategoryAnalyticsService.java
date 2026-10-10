package com.spendio.analytics.service;

import com.spendio.analytics.dto.response.CategoryBreakdownResponse;
import com.spendio.analytics.dto.response.TopSpendingResponse;
import com.spendio.analytics.mapper.AnalyticsMapper;
import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.analytics.repository.AnalyticsQueryRepository.CategoryAmount;
import com.spendio.analytics.repository.AnalyticsQueryRepository.Totals;
import com.spendio.analytics.support.AnalyticsRangeValidator;
import com.spendio.analytics.support.PeriodBucket;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Category breakdown and top-spending rankings (ARCHITECTURE.md D.14): shares are
 * computed against the period total and sum to 100 within rounding; top spending
 * compares the same category set against the immediately preceding period of equal
 * length for the delta.
 */
@Service
public class CategoryAnalyticsService {

    private static final int DEFAULT_BREAKDOWN_LIMIT = 12;
    private static final int DEFAULT_TOP_LIMIT = 10;

    private final AnalyticsQueryRepository query;
    private final AnalyticsMapper mapper;
    private final AnalyticsCacheManager cache;

    public CategoryAnalyticsService(AnalyticsQueryRepository query, AnalyticsMapper mapper,
            AnalyticsCacheManager cache) {
        this.query = query;
        this.mapper = mapper;
        this.cache = cache;
    }

    public List<CategoryBreakdownResponse> categoryBreakdown(Long userId, LocalDate from, LocalDate to,
            String type, Integer limit, Boolean parentOnly) {
        LocalDate start = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate end = to != null ? to : LocalDate.now();
        AnalyticsRangeValidator.validate(start, end, PeriodBucket.DAILY);
        String txType = normalizeType(type);
        int capped = AnalyticsRangeValidator.limit(limit, DEFAULT_BREAKDOWN_LIMIT);
        boolean parents = Boolean.TRUE.equals(parentOnly);

        String key = "category-breakdown:" + start + ":" + end + ":" + txType + ":" + capped + ":" + parents;
        return cache.get(userId, key, List.class, () -> {
            List<CategoryAmount> rows = query.byCategory(userId, start, end, txType, capped, parents);
            long total = 0;
            for (CategoryAmount row : rows) {
                total += row.amountMinor();
            }
            List<CategoryBreakdownResponse> result = new ArrayList<>();
            for (CategoryAmount row : rows) {
                result.add(mapper.toCategoryBreakdown(row, total));
            }
            return result;
        });
    }

    public List<TopSpendingResponse> topSpending(Long userId, LocalDate from, LocalDate to, Integer limit) {
        LocalDate start = from != null ? from : LocalDate.now().minusDays(29);
        LocalDate end = to != null ? to : LocalDate.now();
        AnalyticsRangeValidator.validate(start, end, PeriodBucket.DAILY);
        int capped = AnalyticsRangeValidator.limit(limit, DEFAULT_TOP_LIMIT);

        String key = "top-spending:" + start + ":" + end + ":" + capped;
        return cache.get(userId, key, List.class, () -> buildTopSpending(userId, start, end, capped));
    }

    private List<TopSpendingResponse> buildTopSpending(Long userId, LocalDate start, LocalDate end, int limit) {
        long spanDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(spanDays - 1);

        List<CategoryAmount> current = query.byCategory(userId, start, end, "EXPENSE", limit, false);
        Totals currentTotals = query.totals(userId, start, end);
        List<CategoryAmount> previous = query.byCategory(userId, previousStart, previousEnd, "EXPENSE",
                AnalyticsRangeValidator.MAX_LIMIT, false);

        Map<Object, Long> previousByKey = new HashMap<>();
        for (CategoryAmount row : previous) {
            previousByKey.put(row.categoryId() == null ? "uncategorized" : row.categoryId(),
                    row.amountMinor());
        }

        List<TopSpendingResponse> result = new ArrayList<>();
        int rank = 1;
        for (CategoryAmount row : current) {
            Object key = row.categoryId() == null ? "uncategorized" : row.categoryId();
            long previousAmount = previousByKey.getOrDefault(key, 0L);
            String name = row.name() == null || row.name().isBlank()
                    ? CategoryBreakdownResponse.UNCATEGORIZED
                    : row.name();
            result.add(new TopSpendingResponse(
                    rank++,
                    row.categoryId(),
                    name,
                    row.amountMinor(),
                    mapper.sharePct(row.amountMinor(), currentTotals.expenseMinor()),
                    mapper.deltaPct(row.amountMinor(), previousAmount)));
        }
        return result;
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toUpperCase();
        if (normalized.equals("INCOME") || normalized.equals("EXPENSE")) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "type must be INCOME or EXPENSE")
                .withProperty("type", type);
    }
}
