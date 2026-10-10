package com.spendio.analytics.service;

import com.spendio.analytics.dto.response.MonthlyComparisonResponse;
import com.spendio.analytics.mapper.AnalyticsMapper;
import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.analytics.repository.AnalyticsQueryRepository.MonthTotals;
import com.spendio.analytics.support.AnalyticsRangeValidator;
import com.spendio.analytics.support.PeriodBucket;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Year-long monthly comparison (ARCHITECTURE.md D.14
 * {@code GET /analytics/monthly-comparison}): twelve rows of income/expense/net with
 * the net change vs the previous month, reaching one month back for January's
 * comparison figure.
 */
@Service
public class SummaryAnalyticsService {

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final AnalyticsQueryRepository query;
    private final AnalyticsMapper mapper;
    private final AnalyticsCacheManager cache;

    public SummaryAnalyticsService(AnalyticsQueryRepository query, AnalyticsMapper mapper,
            AnalyticsCacheManager cache) {
        this.query = query;
        this.mapper = mapper;
        this.cache = cache;
    }

    public List<MonthlyComparisonResponse> monthlyComparison(Long userId, Integer year) {
        int targetYear = year == null ? Year.now().getValue() : year;
        if (targetYear < MIN_YEAR || targetYear > MAX_YEAR) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "year is out of range")
                    .withProperty("year", targetYear);
        }
        String key = "monthly-comparison:" + targetYear;
        return cache.get(userId, key, List.class, () -> build(userId, targetYear));
    }

    private List<MonthlyComparisonResponse> build(Long userId, int year) {
        // One extra month back so January has a previous-net figure to compare against.
        java.time.LocalDate from = YearMonth.of(year - 1, 12).atDay(1);
        java.time.LocalDate to = YearMonth.of(year, 12).atEndOfMonth();
        AnalyticsRangeValidator.validate(from, to, PeriodBucket.MONTHLY);

        Map<String, MonthTotals> byMonth = new HashMap<>();
        for (MonthTotals row : query.monthlyTotals(userId, from, to)) {
            byMonth.put(row.year() + "-" + String.format("%02d", row.month()), row);
        }

        List<MonthlyComparisonResponse> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            String label = year + "-" + String.format("%02d", month);
            MonthTotals current = byMonth.getOrDefault(label, empty(year, month));
            long net = current.incomeMinor() - current.expenseMinor();

            int previousMonth = month == 1 ? 12 : month - 1;
            int previousYear = month == 1 ? year - 1 : year;
            String previousLabel = previousYear + "-" + String.format("%02d", previousMonth);
            MonthTotals previous = byMonth.getOrDefault(previousLabel, empty(previousYear, previousMonth));
            long previousNet = previous.incomeMinor() - previous.expenseMinor();

            result.add(new MonthlyComparisonResponse(
                    label,
                    current.incomeMinor(),
                    current.expenseMinor(),
                    net,
                    mapper.deltaPct(net, previousNet)));
        }
        return result;
    }

    private static MonthTotals empty(int year, int month) {
        return new MonthTotals(year, month, 0, 0);
    }
}
