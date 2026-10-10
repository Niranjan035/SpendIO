package com.spendio.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.analytics.dto.response.CategoryBreakdownResponse;
import com.spendio.analytics.dto.response.TopSpendingResponse;
import com.spendio.analytics.mapper.AnalyticsMapper;
import com.spendio.analytics.repository.AnalyticsQueryRepository;
import com.spendio.analytics.repository.AnalyticsQueryRepository.CategoryAmount;
import com.spendio.analytics.repository.AnalyticsQueryRepository.Totals;
import com.spendio.analytics.support.AnalyticsRangeValidator;
import com.spendio.sharedkernel.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Shares, deltas and clamping for the category views (ARCHITECTURE.md D.14,
 * "shares sum to 100"): every row's share is computed against the period total, the
 * delta compares against the preceding period of equal length, and the limit is
 * capped by the validator. The repository is mocked - the real SQL is covered by
 * {@code AnalyticsQueryRepositoryTest} against MySQL.
 */
class CategoryAnalyticsServiceTest {

    private static final long USER = 9;
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    private AnalyticsQueryRepository query;
    private CategoryAnalyticsService service;

    @BeforeEach
    void setUp() {
        query = mock(AnalyticsQueryRepository.class);
        org.springframework.cache.CacheManager cacheManager = mock(org.springframework.cache.CacheManager.class);
        org.springframework.cache.Cache cache = mock(org.springframework.cache.Cache.class);
        org.springframework.cache.Cache.ValueWrapper wrapper = mock(org.springframework.cache.Cache.ValueWrapper.class);
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(cache.get(any())).thenReturn(wrapper);
        when(wrapper.get()).thenReturn(null);
        service = new CategoryAnalyticsService(query, new AnalyticsMapper(),
                new AnalyticsCacheManager(cacheManager));
    }

    @Test
    void breakdownSharesSumToOneHundred() {
        when(query.byCategory(eq(USER), eq(FROM), eq(TO), eq("EXPENSE"), eq(12), eq(false)))
                .thenReturn(List.of(
                        row(1L, "Groceries", "#FF0000", 6_000, 6),
                        row(2L, "Dining", "#00FF00", 3_000, 3),
                        row(null, null, null, 1_000, 1)));

        List<CategoryBreakdownResponse> breakdown =
                service.categoryBreakdown(USER, FROM, TO, "EXPENSE", null, false);

        BigDecimal sum = breakdown.stream()
                .map(CategoryBreakdownResponse::sharePct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(breakdown).hasSize(3);
        assertThat(sum).isEqualByComparingTo("100.0");
        assertThat(breakdown.get(0).sharePct()).isEqualByComparingTo("60.0");
        assertThat(breakdown.get(2).name()).isEqualTo(CategoryBreakdownResponse.UNCATEGORIZED);
    }

    @Test
    void roundingNeverDriftsSharesBeyondHalfAPoint() {
        when(query.byCategory(eq(USER), eq(FROM), eq(TO), eq("EXPENSE"), eq(12), eq(false)))
                .thenReturn(List.of(row(1L, "A", null, 1_00, 1), row(2L, "B", null, 1_00, 1),
                        row(3L, "C", null, 1_00, 1)));

        List<CategoryBreakdownResponse> breakdown =
                service.categoryBreakdown(USER, FROM, TO, "EXPENSE", null, false);

        BigDecimal sum = breakdown.stream()
                .map(CategoryBreakdownResponse::sharePct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(breakdown.get(0).sharePct()).isEqualByComparingTo("33.3");
        assertThat(Math.abs(sum.doubleValue() - 100.0)).isLessThan(0.5);
    }

    @Test
    void invalidTypeIsRejected() {
        assertThatThrownBy(() -> service.categoryBreakdown(USER, FROM, TO, "TRANSFER", null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void topSpendingComparesAgainstThePrecedingPeriodAndClampsTheLimit() {
        when(query.byCategory(eq(USER), eq(FROM), eq(TO), eq("EXPENSE"), eq(10), eq(false)))
                .thenReturn(List.of(
                        row(1L, "Rent", null, 150_00, 1),
                        row(null, null, null, 20_00, 1)));
        when(query.byCategory(eq(USER),
                eq(FROM.minusDays(30)), eq(TO.minusDays(30)), eq("EXPENSE"), eq(AnalyticsRangeValidator.MAX_LIMIT),
                eq(false)))
                .thenReturn(List.of(row(1L, "Rent", null, 100_00, 1)));
        when(query.totals(eq(USER), eq(FROM), eq(TO))).thenReturn(new Totals(0, 170_00, 2));

        List<TopSpendingResponse> top = service.topSpending(USER, FROM, TO, 10);

        assertThat(top).hasSize(2);
        assertThat(top.get(0).categoryId()).isEqualTo(1L);
        assertThat(top.get(0).sharePct()).isEqualByComparingTo("88.2");
        assertThat(top.get(0).deltaPct()).isEqualByComparingTo("50.0");
        assertThat(top.get(1).deltaPct()).isNull();
        assertThat(top.get(1).name()).isEqualTo(CategoryBreakdownResponse.UNCATEGORIZED);

        when(query.byCategory(eq(USER), eq(FROM), eq(TO), eq("EXPENSE"),
                eq(AnalyticsRangeValidator.MAX_LIMIT), eq(false)))
                .thenReturn(List.of(row(1L, "Rent", null, 150_00, 1)));

        service.topSpending(USER, FROM, TO, 500);

        verify(query, org.mockito.Mockito.times(3)).byCategory(eq(USER), any(LocalDate.class), any(LocalDate.class),
                eq("EXPENSE"), eq(AnalyticsRangeValidator.MAX_LIMIT), eq(false));
    }

    private static CategoryAmount row(Long categoryId, String name, String color, long amount, long count) {
        return new CategoryAmount(categoryId, name, color, amount, count);
    }
}