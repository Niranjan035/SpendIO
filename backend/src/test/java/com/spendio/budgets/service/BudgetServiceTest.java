package com.spendio.budgets.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.dto.request.CreateBudgetRequest;
import com.spendio.budgets.mapper.BudgetMapper;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Budget creation and edit guards (ARCHITECTURE.md D.10): canonical windows, the
 * expense-only scope, the one-overall-budget-per-period rule, name uniqueness and the
 * base freeze on create.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final long USER = 1L;
    private static final long CATEGORY = 10L;

    @Mock
    private BudgetRepository budgets;
    @Mock
    private CategoryQueryService categoryQuery;
    @Mock
    private BaseCurrencyPort baseCurrency;
    @Mock
    private ObjectProvider<FxConversionPort> fxPort;
    @Mock
    private BudgetMapper mapper;
    @Mock
    private BudgetAlertService alerts;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Category categoryEntity;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgets, categoryQuery, baseCurrency, fxPort, mapper, alerts, entityManager);
        lenient().when(baseCurrency.baseCurrencyCode(USER)).thenReturn("USD");
        lenient().when(categoryEntity.getId()).thenReturn(CATEGORY);
        lenient().when(entityManager.getReference(eq(Category.class), eq(CATEGORY)))
                .thenReturn(categoryEntity);
    }

    @Test
    void createsAnExpenseBudgetFrozenInTheBaseCurrency() {
        stubExpenseCategory();
        when(budgets.existsByUserIdAndNameAndDeletedAtIsNull(USER, "Groceries")).thenReturn(false);

        service.create(USER, request(
                "Groceries", CATEGORY, Budget.PeriodType.WEEKLY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11), 100_000));

        java.util.List<Budget> captured = verifySaved();
        Budget saved = captured.get(0);
        assertThat(saved.getPeriodType()).isEqualTo(Budget.PeriodType.WEEKLY);
        assertThat(saved.getAmountMinor()).isEqualTo(100_000);
        assertThat(saved.getBaseAmountMinor()).isEqualTo(100_000);
        assertThat(saved.isRolloverEnabled()).isFalse();
        assertThat(saved.getAlertThresholdPct()).isEqualTo((short) 80);
        verify(alerts).evaluate(any(Budget.class));
    }

    @Test
    void rejectsAnIncomeCategoryScope() {
        stubCategory(new CategoryResponse(CATEGORY, USER, null, "Salary", Category.Type.INCOME,
                null, null, false, false, 0, false, Category.Status.ACTIVE,
                null, null, null));

        assertThatThrownBy(() -> service.create(USER, request(
                "Salary budget", CATEGORY, Budget.PeriodType.MONTHLY,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), 50_000)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUDGET_CATEGORY_TYPE_MISMATCH));
        verify(budgets, never()).save(any(Budget.class));
    }

    @Test
    void rejectsNonCanonicalWindowsByPeriodType() {
        assertThatThrownBy(() -> service.create(USER, request(
                "Broken", null, Budget.PeriodType.WEEKLY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12), 100_000)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUDGET_PERIOD_INVALID));

        assertThatThrownBy(() -> service.create(USER, request(
                "Broken month", null, Budget.PeriodType.MONTHLY,
                LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 28), 100_000)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUDGET_PERIOD_INVALID));
        verify(budgets, never()).save(any(Budget.class));
    }

    @Test
    void rejectsAnOverlappingOverallBudget() {
        cuandoOverallOverlaps(true);
        assertThatThrownBy(() -> service.create(USER, request(
                "Overall", null, Budget.PeriodType.MONTHLY,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), 100_000)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUDGET_OVERLAPPING_OVERALL));
        verify(budgets, never()).save(any(Budget.class));
    }

    @Test
    void rejectsADuplicateName() {
        stubExpenseCategory();
        when(budgets.existsByUserIdAndNameAndDeletedAtIsNull(USER, "Groceries")).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER, request(
                "Groceries", CATEGORY, Budget.PeriodType.WEEKLY,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11), 100_000)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE));
    }

    private void stubExpenseCategory() {
        stubCategory(new CategoryResponse(CATEGORY, USER, null, "Groceries", Category.Type.EXPENSE,
                null, null, false, false, 0, false, Category.Status.ACTIVE,
                null, null, null));
    }

    private void stubCategory(CategoryResponse response) {
        when(categoryQuery.detail(USER, CATEGORY)).thenReturn(response);
    }

    private void cuandoOverallOverlaps(boolean value) {
        when(budgets.existsOverlappingOverallBudget(eq(USER), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(value);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Budget> verifySaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(Budget.class);
        verify(budgets).save(captor.capture());
        return captor.getAllValues();
    }

    private static CreateBudgetRequest request(String name, Long categoryId, Budget.PeriodType periodType,
            LocalDate start, LocalDate end, long amount) {
        return new CreateBudgetRequest(name, categoryId, periodType, start, end, amount, "USD",
                false, 80, true, true);
    }
}