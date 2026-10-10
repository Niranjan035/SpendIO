package com.spendio.budgets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.domain.PaceStatus;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.dto.response.BudgetResponse;
import com.spendio.budgets.mapper.BudgetMapper;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.sharedkernel.domain.enums.TransactionType;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.service.LedgerSpendService;
import com.spendio.transactions.service.LedgerSpendService.CategorySpend;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Budget progress maths (ARCHITECTURE.md D.10/K.5): the spend rollup restricted to the
 * budget's category scope, exclusion rules, utilisation, projection and the pace band.
 * All values are hand-computed in minor units and base == budget currency (USD) so no
 * FX conversion interferes with asserting the arithmetic itself.
 */
@ExtendWith(MockitoExtension.class)
class BudgetProgressServiceTest {

    private static final long USER = 1L;
    private static final long FOOD = 10L;
    private static final long DINING = 11L;
    private static final LocalDate FROM = LocalDate.of(2026, 2, 1);
    private static final LocalDate TO = LocalDate.of(2026, 2, 28);

    @Mock
    private BudgetRepository budgets;
    @Mock
    private CategoryQueryService categoryQuery;
    @Mock
    private LedgerSpendService ledger;
    @Mock
    private BaseCurrencyPort baseCurrency;
    @Mock
    private ObjectProvider<FxConversionPort> fxPort;
    @Mock
    private BudgetMapper mapper;

    private BudgetProgressService service;

    @BeforeEach
    void setUp() {
        service = new BudgetProgressService(budgets, categoryQuery, ledger, baseCurrency, fxPort, mapper);
        lenient().when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        lenient().when(fxPort.getIfAvailable()).thenReturn(null);
        lenient().when(mapper.toResponse(org.mockito.ArgumentMatchers.any(Budget.class)))
                .thenReturn(mock(BudgetResponse.class));
    }

    @Test
    void utilisesOnlyExpenseSpendInScopeForAnOverallBudget() {
        scope(category(FOOD, "Food", Category.Type.EXPENSE, null, false),
                category(DINING, "Dining", Category.Type.EXPENSE, FOOD, false),
                category(10_002L, "Salary", Category.Type.INCOME, null, false));
        spend(new CategorySpend(FOOD, TransactionType.EXPENSE, 20_000),
                new CategorySpend(DINING, TransactionType.EXPENSE, 5_000),
                new CategorySpend(10_002L, TransactionType.INCOME, 9_999_999)); // must be ignored

        Budget budget = budget(null, 100_000, FROM, TO);

        BudgetProgressResponse progress = service.compute(USER, budget, FROM, TO);

        assertThat(progress.spentMinor()).isEqualTo(25_000);
        assertThat(progress.remainingMinor()).isEqualTo(75_000);
        assertThat(progress.overBudget()).isFalse();
        assertThat(progress.utilisationPct()).isEqualByComparingTo("25.00");
        assertThat(progress.categoryBreakdown()).hasSize(2);
        assertThat(progress.categoryBreakdown().get(0).categoryId()).isEqualTo(FOOD);
        assertThat(progress.categoryBreakdown().get(0).sharePct()).isEqualByComparingTo("80.0");
        assertThat(progress.daysElapsed()).isEqualTo(28);
        assertThat(progress.daysRemaining()).isZero();
        assertThat(progress.projectedSpendMinor()).isEqualTo(25_000);
    }

    @Test
    void categoryBudgetIncludesSubtreeAndDropsExcludedCategories() {
        scope(category(FOOD, "Food", Category.Type.EXPENSE, null, false),
                category(DINING, "Dining", Category.Type.EXPENSE, FOOD, true),   // excluded
                category(12L, "Delivery", Category.Type.EXPENSE, DINING, false)); // under excluded root
        spend(new CategorySpend(FOOD, TransactionType.EXPENSE, 10_000),
                new CategorySpend(DINING, TransactionType.EXPENSE, 5_000),
                new CategorySpend(12L, TransactionType.EXPENSE, 2_000));

        Budget budget = Budget.create(USER, "Food", ref(FOOD), Budget.PeriodType.MONTHLY,
                FROM, TO, 15_000, "USD", 15_000, false, (short) 80, true, true);

        BudgetProgressResponse progress = service.compute(USER, budget, FROM, TO);

        // Delivery belongs under the excluded Dining root, so only Food itself counts.
        assertThat(progress.spentMinor()).isEqualTo(10_000);
        assertThat(progress.categoryBreakdown()).extracting("categoryId").containsExactly(FOOD);
    }

    @Test
    void exceedsWhenSpendMeetsAllowance() {
        scope(category(FOOD, "Food", Category.Type.EXPENSE, null, false));
        spend(new CategorySpend(FOOD, TransactionType.EXPENSE, 100_000));

        BudgetProgressResponse response = service.compute(USER, budget(null, 100_000, FROM, TO), FROM, TO);
        assertThat(response.overBudget()).isTrue();
        assertThat(response.remainingMinor()).isZero();
    }

    @Test
    void paceBandClassifiesUnderOverAndOn() {
        Budget budget = budget(null, 100_000, FROM, TO);
        // Half the period, half the allowance spent.
        assertThat(service.pace(50_000, 100_000, 14, 28)).isEqualTo(PaceStatus.ON_PACE);
        assertThat(service.pace(60_000, 100_000, 14, 28)).isEqualTo(PaceStatus.OVER_PACE);
        assertThat(service.pace(40_000, 100_000, 14, 28)).isEqualTo(PaceStatus.UNDER_PACE);
        assertThat(service.pace(100_000, 100_000, 14, 28)).isEqualTo(PaceStatus.EXCEEDED);
    }

    @Test
    void scopeDropsNothingWhenNoExpenseCategoriesExist() {
        when(categoryQuery.allActive(USER)).thenReturn(List.of());
        Budget budget = budget(null, 100_000, FROM, TO);
        assertThat(service.resolveScope(USER, budget)).isEmpty();
    }

    private static CategoryResponse category(Long id, String name, Category.Type type, Long parentId,
            boolean excludeFromBudgets) {
        return new CategoryResponse(id, USER, parentId, name, type, null, null, false, false, 0,
                excludeFromBudgets, Category.Status.ACTIVE, null, null, null);
    }

    private void scope(CategoryResponse... categories) {
        when(categoryQuery.allActive(USER)).thenReturn(List.of(categories));
    }

    private void spend(CategorySpend... rows) {
        when(ledger.spendByCategory(eq(USER), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(rows));
    }

    private static Budget budget(Long categoryId, long amount, LocalDate from, LocalDate to) {
        Category category = categoryId == null ? null : ref(categoryId);
        return Budget.create(USER, "Budget", category, Budget.PeriodType.CUSTOM, from, to, amount, "USD", amount,
                false, (short) 80, true, true);
    }

    private static Category ref(Long id) {
        Category category = Mockito.mock(Category.class);
        lenient().when(category.getId()).thenReturn(id);
        return category;
    }
}