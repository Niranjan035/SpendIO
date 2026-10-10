package com.spendio.analytics.service;

import com.spendio.analytics.dto.response.CashFlowPointResponse;
import com.spendio.analytics.dto.response.SavingsRatePointResponse;
import com.spendio.analytics.dto.response.SpendingTrendPointResponse;
import com.spendio.analytics.mapper.AnalyticsMapper;
import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.analytics.repository.AnalyticsQueryRepository.DailyFlow;
import com.spendio.analytics.support.AnalyticsRangeValidator;
import com.spendio.analytics.support.PeriodBucket;
import com.spendio.budgets.dto.response.BudgetResponse;
import com.spendio.budgets.service.BudgetQueryService;
import com.spendio.budgets.spec.BudgetFilter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Time-series analytics (ARCHITECTURE.md D.14): cash flow, the spending trend with
 * a prorated budget overlay, and income vs expense (savings rate). Daily rows are
 * fetched once and bucketed in Java so day/week/month boundaries never depend on
 * SQL dialect quirks (D.14: bucket-boundary tests).
 */
@Service
public class TrendAnalyticsService {

    private final AnalyticsQueryRepository query;
    private final AnalyticsMapper mapper;
    private final AnalyticsCacheManager cache;
    private final BudgetQueryService budgets;

    public TrendAnalyticsService(AnalyticsQueryRepository query, AnalyticsMapper mapper,
            AnalyticsCacheManager cache, BudgetQueryService budgets) {
        this.query = query;
        this.mapper = mapper;
        this.cache = cache;
        this.budgets = budgets;
    }

    public List<CashFlowPointResponse> cashFlow(Long userId, LocalDate from, LocalDate to, String granularity) {
        PeriodBucket bucket = PeriodBucket.parse(granularity);
        Range range = resolve(from, to, bucket);
        String key = "cash-flow:" + range.from() + ":" + range.to() + ":" + bucket;
        return cache.get(userId, key, List.class, () -> {
            List<CashFlowPointResponse> points = new ArrayList<>();
            for (BucketTotals totals : bucketTotals(userId, range, bucket)) {
                points.add(new CashFlowPointResponse(
                        totals.label,
                        totals.income,
                        totals.expense,
                        totals.income - totals.expense));
            }
            return points;
        });
    }

    public List<SpendingTrendPointResponse> spendingTrend(
            Long userId, LocalDate from, LocalDate to, String granularity) {
        PeriodBucket bucket = PeriodBucket.parse(granularity);
        Range range = resolve(from, to, bucket);
        String key = "spending-trend:" + range.from() + ":" + range.to() + ":" + bucket;
        return cache.get(userId, key, List.class, () -> buildTrend(userId, range, bucket));
    }

    public List<SavingsRatePointResponse> incomeVsExpense(
            Long userId, LocalDate from, LocalDate to, String granularity) {
        PeriodBucket bucket = PeriodBucket.parse(granularity);
        Range range = resolve(from, to, bucket);
        String key = "income-vs-expense:" + range.from() + ":" + range.to() + ":" + bucket;
        return cache.get(userId, key, List.class, () -> {
            List<SavingsRatePointResponse> points = new ArrayList<>();
            for (BucketTotals totals : bucketTotals(userId, range, bucket)) {
                long savings = totals.income - totals.expense;
                points.add(new SavingsRatePointResponse(
                        totals.label, totals.income, totals.expense, savings,
                        mapper.savingsRatePct(totals.income, totals.expense)));
            }
            return points;
        });
    }

    private List<SpendingTrendPointResponse> buildTrend(Long userId, Range range, PeriodBucket bucket) {
        Map<String, Long> expenseByBucket = new LinkedHashMap<>();
        for (BucketTotals totals : bucketTotals(userId, range, bucket)) {
            expenseByBucket.put(totals.label, totals.expense);
        }
        Map<String, Long> budgetByBucket = budgetOverlay(userId, range, bucket);

        List<SpendingTrendPointResponse> points = new ArrayList<>();
        long cumulative = 0;
        for (Map.Entry<String, Long> entry : expenseByBucket.entrySet()) {
            long expense = entry.getValue();
            cumulative += expense;
            points.add(new SpendingTrendPointResponse(
                    entry.getKey(),
                    expense,
                    budgetByBucket.getOrDefault(entry.getKey(), 0L),
                    cumulative));
        }
        return points;
    }

    /**
     * Prorates each active budget's allowance evenly across the buckets its period
     * overlaps, so the overlay sits at the right height whatever the granularity is.
     */
    private Map<String, Long> budgetOverlay(Long userId, Range range, PeriodBucket bucket) {
        Map<String, Long> overlay = new LinkedHashMap<>();
        List<BudgetResponse> active = budgets
                .list(userId, new BudgetFilter(com.spendio.budgets.domain.Budget.Status.ACTIVE,
                        null, null, null), 0, 100)
                .content();
        for (BudgetResponse budget : active) {
            LocalDate from = budget.periodStart().isBefore(range.from()) ? range.from() : budget.periodStart();
            LocalDate to = budget.periodEnd().isAfter(range.to()) ? range.to() : budget.periodEnd();
            if (from.isAfter(to)) {
                continue;
            }
            List<String> labels = bucketLabels(from, to, bucket);
            if (labels.isEmpty()) {
                continue;
            }
            long share = budget.amountMinor() / labels.size();
            for (String label : labels) {
                overlay.merge(label, share, Long::sum);
            }
        }
        return overlay;
    }

    /** One row per calendar day, folded into the requested bucket. */
    private List<BucketTotals> bucketTotals(Long userId, Range range, PeriodBucket bucket) {
        Map<String, BucketTotals> buckets = new LinkedHashMap<>();
        for (String label : bucketLabels(range.from(), range.to(), bucket)) {
            buckets.put(label, new BucketTotals(label));
        }
        for (DailyFlow day : query.dailyFlow(userId, range.from(), range.to())) {
            BucketTotals totals = buckets.get(bucket.label(day.day()));
            if (totals != null) {
                totals.income += day.incomeMinor();
                totals.expense += day.expenseMinor();
            }
        }
        return List.copyOf(buckets.values());
    }

    /** Every bucket label in the inclusive range, in chronological order. */
    private static List<String> bucketLabels(LocalDate from, LocalDate to, PeriodBucket bucket) {
        List<String> labels = new ArrayList<>();
        String previous = null;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            String label = bucket.label(day);
            if (!label.equals(previous)) {
                labels.add(label);
                previous = label;
            }
        }
        return labels;
    }

    /** from/to default to a granularity-appropriate window ending today. */
    private static Range resolve(LocalDate from, LocalDate to, PeriodBucket bucket) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start;
        if (from != null) {
            start = from;
        } else {
            start = switch (bucket) {
                case DAILY -> end.minusDays(29);
                case WEEKLY -> end.minusDays(89);
                case MONTHLY -> end.minusDays(364);
            };
        }
        AnalyticsRangeValidator.validate(start, end, bucket);
        return new Range(start, end);
    }

    private record Range(LocalDate from, LocalDate to) {
    }

    private static final class BucketTotals {
        private final String label;
        private long income;
        private long expense;

        private BucketTotals(String label) {
            this.label = label;
        }
    }
}
