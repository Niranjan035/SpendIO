package com.spendio.budgets.service;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.dto.request.CreateBudgetRequest;
import com.spendio.budgets.dto.request.UpdateBudgetRequest;
import com.spendio.budgets.dto.response.BudgetResponse;
import com.spendio.budgets.mapper.BudgetMapper;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.categories.domain.Category;
import com.spendio.categories.dto.response.CategoryResponse;
import com.spendio.categories.service.CategoryQueryService;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.support.LedgerFx;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget lifecycle (ARCHITECTURE.md D.10): create with a frozen base-currency amount,
 * partial edits that re-validate the merged state, soft delete, and an alert
 * evaluation after every relevant write (the nightly job covers the rest).
 *
 * <p>Windows must match the canonical boundaries of their period type - that
 * contract is what lets {@link com.spendio.budgets.job.BudgetRolloverJob} advance any
 * budget by exactly one period with simple date arithmetic.
 */
@Service
public class BudgetService {

    private static final int MAX_CUSTOM_DAYS = 731;

    private final BudgetRepository budgets;
    private final CategoryQueryService categoryQuery;
    private final BaseCurrencyPort baseCurrency;
    private final ObjectProvider<FxConversionPort> fxPort;
    private final BudgetMapper mapper;
    private final BudgetAlertService alerts;
    private final EntityManager entityManager;

    public BudgetService(BudgetRepository budgets, CategoryQueryService categoryQuery,
            BaseCurrencyPort baseCurrency, ObjectProvider<FxConversionPort> fxPort, BudgetMapper mapper,
            BudgetAlertService alerts, EntityManager entityManager) {
        this.budgets = budgets;
        this.categoryQuery = categoryQuery;
        this.baseCurrency = baseCurrency;
        this.fxPort = fxPort;
        this.mapper = mapper;
        this.alerts = alerts;
        this.entityManager = entityManager;
    }

    @Transactional
    public BudgetResponse create(Long userId, CreateBudgetRequest request) {
        String name = requireName(request.name());
        LocalDate start = request.periodStart();
        LocalDate end = request.periodEnd();
        validateWindow(request.periodType(), start, end);

        Category category = resolveCategory(userId, request.categoryId());
        if (category == null && budgets.existsOverlappingOverallBudget(userId, start, end)) {
            throw new BusinessException(ErrorCode.BUDGET_OVERLAPPING_OVERALL,
                    "An overall budget already covers part of that period")
                    .withProperty("periodStart", start)
                    .withProperty("periodEnd", end);
        }
        requireNameFree(userId, name, null);

        String currency = normalize(request.currencyCode());
        long baseAmount = LedgerFx.freeze(baseCurrency.baseCurrencyCode(userId), fxPort.getIfAvailable(),
                request.amountMinor(), currency, start).baseAmountMinor();

        Budget budget = Budget.create(
                userId,
                name,
                category,
                request.periodType(),
                start,
                end,
                request.amountMinor(),
                currency,
                baseAmount,
                Boolean.TRUE.equals(request.rolloverEnabled()),
                request.alertThresholdPct() == null ? 80 : request.alertThresholdPct().shortValue(),
                !Boolean.FALSE.equals(request.alertEnabled()),
                !Boolean.FALSE.equals(request.includeSubcategories()));
        budgets.save(budget);
        alerts.evaluate(budget);
        return mapper.toResponse(budget);
    }

    @Transactional
    public BudgetResponse update(Long userId, Long id, UpdateBudgetRequest request) {
        Budget budget = budgets.findOwnedForUpdate(userId, id).orElseThrow(() -> notFound(id));
        if (request.version() != null && !request.version().equals(budget.getVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT, "The budget was modified by another request")
                    .withProperty("expectedVersion", request.version())
                    .withProperty("currentVersion", budget.getVersion());
        }

        if (request.name() != null) {
            String name = requireName(request.name());
            requireNameFree(userId, name, budget.getId());
            budget.setName(name);
        }

        Budget.PeriodType periodType = request.periodType() != null ? request.periodType() : budget.getPeriodType();
        LocalDate start = request.periodStart() != null ? request.periodStart() : budget.getPeriodStart();
        LocalDate end = request.periodEnd() != null ? request.periodEnd() : budget.getPeriodEnd();
        boolean windowChanged = request.periodType() != null || request.periodStart() != null
                || request.periodEnd() != null;
        if (windowChanged || request.amountMinor() != null || request.currencyCode() != null) {
            validateWindow(periodType, start, end);
        }
        budget.setPeriodType(periodType);
        budget.setPeriod(start, end);

        if (request.categoryId() != null) {
            budget.setCategory(resolveCategory(userId, request.categoryId()));
        }

        String currency = request.currencyCode() != null
                ? normalize(request.currencyCode())
                : budget.getCurrencyCode();
        long amount = request.amountMinor() != null ? request.amountMinor() : budget.getAmountMinor();
        if (request.amountMinor() != null || request.currencyCode() != null) {
            long baseAmount = LedgerFx.freeze(baseCurrency.baseCurrencyCode(userId), fxPort.getIfAvailable(),
                    amount, currency, start).baseAmountMinor();
            budget.setBaseAmountMinor(baseAmount);
        }
        budget.setCurrencyCode(currency);
        budget.setAmountMinor(amount);

        if (request.rolloverEnabled() != null) {
            budget.setRolloverEnabled(request.rolloverEnabled());
        }
        if (request.alertThresholdPct() != null) {
            budget.setAlertThresholdPct(request.alertThresholdPct().shortValue());
        }
        if (request.alertEnabled() != null) {
            budget.setAlertEnabled(request.alertEnabled());
        }
        if (request.includeSubcategories() != null) {
            budget.setIncludeSubcategories(request.includeSubcategories());
        }
        if (request.status() != null) {
            budget.setStatus(request.status());
        }

        if (budget.getStatus() == Budget.Status.ACTIVE && budget.isAlertEnabled()) {
            alerts.evaluate(budget);
        }
        return mapper.toResponse(budget);
    }

    /** Soft delete: alerts and history stay, the budget disappears from every read. */
    @Transactional
    public void delete(Long userId, Long id) {
        Budget budget = budgets.findOwnedForUpdate(userId, id).orElseThrow(() -> notFound(id));
        budget.softDelete();
    }

    /**
     * Window rules (D.10): WEEKLY is 7 consecutive days, MONTHLY/QUARTERLY/YEARLY must
     * open on the first day and close on the last day of their period, CUSTOM is any
     * inclusive window up to two years. The strictness is what makes rollover
     * advancement well-defined for every stored budget.
     */
    static void validateWindow(Budget.PeriodType type, LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            throw periodInvalid(start, end);
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        boolean valid = switch (type) {
            case WEEKLY -> days == 7;
            case MONTHLY -> start.getDayOfMonth() == 1
                    && end.equals(start.withDayOfMonth(start.lengthOfMonth()))
                    && start.getYear() == end.getYear()
                    && start.getMonth() == end.getMonth();
            case QUARTERLY -> isQuarterStart(start)
                    && end.equals(start.plusMonths(3).withDayOfMonth(1).minusDays(1));
            case YEARLY -> start.equals(LocalDate.of(start.getYear(), 1, 1))
                    && end.equals(LocalDate.of(start.getYear(), 12, 31));
            case CUSTOM -> days <= MAX_CUSTOM_DAYS;
        };
        if (!valid) {
            throw periodInvalid(start, end);
        }
    }

    private static boolean isQuarterStart(LocalDate date) {
        return date.getDayOfMonth() == 1 && (date.getMonthValue() - 1) % 3 == 0;
    }

    private static BusinessException periodInvalid(LocalDate start, LocalDate end) {
        return new BusinessException(ErrorCode.BUDGET_PERIOD_INVALID, "Invalid budget period")
                .withProperty("periodStart", start)
                .withProperty("periodEnd", end);
    }

    /**
     * Budgets measure spending, so only expense categories qualify - an income
     * category is {@code BUDGET_CATEGORY_TYPE_MISMATCH}, and a category flagged
     * {@code exclude_from_budgets} would silently never move.
     */
    private Category resolveCategory(Long userId, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        CategoryResponse category = categoryQuery.detail(userId, categoryId);
        if (category.type() != Category.Type.EXPENSE) {
            throw new BusinessException(ErrorCode.BUDGET_CATEGORY_TYPE_MISMATCH,
                    "Budget category does not match the budget type")
                    .withProperty("categoryId", categoryId)
                    .withProperty("categoryType", category.type().name());
        }
        if (category.excludeFromBudgets()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("categoryId", "this category is excluded from budgets");
        }
        return entityManager.getReference(Category.class, categoryId);
    }

    private void requireNameFree(Long userId, String name, Long selfId) {
        boolean taken = selfId == null
                ? budgets.existsByUserIdAndNameAndDeletedAtIsNull(userId, name)
                : budgets.existsByUserIdAndNameAndDeletedAtIsNullAndIdNot(userId, name, selfId);
        if (taken) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A budget with that name already exists")
                    .withProperty("name", name);
        }
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed")
                    .withFieldError("name", "must not be blank");
        }
        return trimmed;
    }

    private static String normalize(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private static BusinessException notFound(Long id) {
        return new BusinessException(ErrorCode.BUDGET_NOT_FOUND, "Budget not found")
                .withProperty("id", id);
    }
}
