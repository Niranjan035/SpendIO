package com.spendio.budgets.service;

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
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.service.LedgerSpendService;
import com.spendio.transactions.service.LedgerSpendService.CategorySpend;
import com.spendio.transactions.support.LedgerFx;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget progress (ARCHITECTURE.md D.10): spent, remaining, utilisation, projection,
 * daily allowance, pace and the per-category breakdown, all computed live from the
 * ledger - never cached, so a corrected transaction immediately corrects the budget.
 *
 * <p>Currency: the ledger rollup arrives in the user's base currency and is converted
 * once into the budget's currency, which is the unit every figure in the response uses.
 * Exclusion rules (D.10) are structural - {@link LedgerSpendService} only ever returns
 * POSTED income/expense rows, and the category scope below drops anything flagged
 * {@code exclude_from_budgets}.
 */
@Service
public class BudgetProgressService {

    /** Pace verdicts sit in a +/-10% band around straight-line expected spend. */
    private static final double PACE_BAND = 0.10;

    private final BudgetRepository budgets;
    private final CategoryQueryService categoryQuery;
    private final LedgerSpendService ledger;
    private final BaseCurrencyPort baseCurrency;
    private final ObjectProvider<FxConversionPort> fxPort;
    private final BudgetMapper mapper;

    public BudgetProgressService(BudgetRepository budgets, CategoryQueryService categoryQuery,
            LedgerSpendService ledger, BaseCurrencyPort baseCurrency, ObjectProvider<FxConversionPort> fxPort,
            BudgetMapper mapper) {
        this.budgets = budgets;
        this.categoryQuery = categoryQuery;
        this.ledger = ledger;
        this.baseCurrency = baseCurrency;
        this.fxPort = fxPort;
        this.mapper = mapper;
    }

    /** Progress for one budget over an explicit (or the budget's own) window. */
    @Transactional(readOnly = true)
    public BudgetProgressResponse progress(Long userId, Long budgetId, LocalDate periodStart, LocalDate periodEnd) {
        Budget budget = requireOwned(userId, budgetId);
        LocalDate from = periodStart != null ? periodStart : budget.getPeriodStart();
        LocalDate to = periodEnd != null ? periodEnd : budget.getPeriodEnd();
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.BUDGET_PERIOD_INVALID, "Invalid budget period")
                    .withProperty("periodStart", from)
                    .withProperty("periodEnd", to);
        }
        return compute(userId, budget, from, to);
    }

    /** Every ACTIVE budget containing today, each with its progress (the `/current` read). */
    @Transactional(readOnly = true)
    public List<BudgetProgressResponse> current(Long userId) {
        LocalDate today = LocalDate.now();
        return budgets
                .findByUserIdAndDeletedAtIsNullAndStatusAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                        userId, Budget.Status.ACTIVE, today)
                .stream()
                .map(budget -> compute(userId, budget, budget.getPeriodStart(), budget.getPeriodEnd()))
                .toList();
    }

    /**
     * The calculation itself, package-visible for alert evaluation and the rollover
     * job (same module, one definition of "spent").
     */
    @Transactional(readOnly = true)
    public BudgetProgressResponse compute(Long userId, Budget budget, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate asOf = today.isBefore(from) ? from : (today.isAfter(to) ? to : today);

        int daysInPeriod = (int) ChronoUnit.DAYS.between(from, to) + 1;
        int daysElapsed = (int) ChronoUnit.DAYS.between(from, asOf) + 1;
        int daysRemaining = (int) ChronoUnit.DAYS.between(asOf, to);

        String baseCode = baseCurrency.baseCurrencyCode(userId);
        Map<Long, CategoryResponse> scope = resolveScope(userId, budget);

        List<BudgetProgressResponse.CategorySlice> slices = new ArrayList<>();
        long spentBase = 0;
        if (!scope.isEmpty()) {
            Map<Long, Long> spentByCategory = new HashMap<>();
            for (CategorySpend row : ledger.spendByCategory(userId, from, to)) {
                if (row.type() == TransactionType.EXPENSE && scope.containsKey(row.categoryId())) {
                    spentByCategory.merge(row.categoryId(), row.baseAmountMinor(), Long::sum);
                }
            }
            for (Map.Entry<Long, Long> entry : spentByCategory.entrySet()) {
                CategoryResponse category = scope.get(entry.getKey());
                slices.add(new BudgetProgressResponse.CategorySlice(
                        category.id(),
                        category.name(),
                        category.color(),
                        toBudgetCurrency(entry.getValue(), baseCode, budget, asOf),
                        null));
                spentBase += entry.getValue();
            }
        }

        long spent = toBudgetCurrency(spentBase, baseCode, budget, asOf);
        long allowance = budget.allowanceMinor();
        long remaining = allowance - spent;
        boolean overBudget = spent >= allowance;

        BigDecimal utilisation = allowance > 0
                ? BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(allowance), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal shareTotal = BigDecimal.valueOf(spent);
        slices.sort(Comparator.comparingLong(BudgetProgressResponse.CategorySlice::spentMinor).reversed());
        List<BudgetProgressResponse.CategorySlice> breakdown = slices.stream()
                .map(slice -> new BudgetProgressResponse.CategorySlice(
                        slice.categoryId(),
                        slice.name(),
                        slice.color(),
                        slice.spentMinor(),
                        shareTotal.signum() > 0
                                ? BigDecimal.valueOf(slice.spentMinor()).multiply(BigDecimal.valueOf(100))
                                        .divide(shareTotal, 1, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO))
                .toList();

        long projected = daysElapsed > 0
                ? Math.round((double) spent * daysInPeriod / daysElapsed)
                : spent;
        long dailyAllowance = daysRemaining > 0 ? Math.max(remaining, 0) / daysRemaining : 0;

        return new BudgetProgressResponse(
                mapper.toResponse(budget),
                new BudgetProgressResponse.Period(from, to, budget.getPeriodType()),
                allowance,
                spent,
                remaining,
                utilisation,
                projected,
                dailyAllowance,
                overBudget,
                pace(spent, allowance, daysElapsed, daysInPeriod),
                daysElapsed,
                daysRemaining,
                breakdown);
    }

    /**
     * Straight-line pace verdict: {@code EXCEEDED} the moment spend meets the
     * allowance, otherwise +/-10% of expected spend to date.
     */
    static PaceStatus pace(long spent, long allowance, int daysElapsed, int daysInPeriod) {
        if (daysElapsed <= 0 || daysInPeriod <= 0) {
            return PaceStatus.ON_PACE;
        }
        if (spent >= allowance) {
            return PaceStatus.EXCEEDED;
        }
        double expected = (double) allowance * daysElapsed / daysInPeriod;
        if (spent > expected * (1 + PACE_BAND)) {
            return PaceStatus.OVER_PACE;
        }
        if (spent < expected * (1 - PACE_BAND)) {
            return PaceStatus.UNDER_PACE;
        }
        return PaceStatus.ON_PACE;
    }

    /**
     * The category ids a budget measures (D.10): an overall budget covers every
     * expense category; a category budget covers itself plus, when enabled, its whole
     * subtree. Anything flagged {@code exclude_from_budgets} is dropped at every level.
     */
    Map<Long, CategoryResponse> resolveScope(Long userId, Budget budget) {
        List<CategoryResponse> all = categoryQuery.allActive(userId);
        Map<Long, CategoryResponse> byId = new LinkedHashMap<>();
        Map<Long, List<Long>> children = new HashMap<>();
        for (CategoryResponse category : all) {
            byId.put(category.id(), category);
            if (category.parentId() != null) {
                children.computeIfAbsent(category.parentId(), key -> new ArrayList<>()).add(category.id());
            }
        }

        Set<Long> ids = new HashSet<>();
        if (budget.getCategory() == null) {
            byId.values().stream()
                    .filter(category -> category.type() == Category.Type.EXPENSE)
                    .filter(category -> !category.excludeFromBudgets())
                    .forEach(category -> ids.add(category.id()));
            return index(byId, ids);
        }

        Long rootId = budget.getCategory().getId();
        CategoryResponse root = byId.get(rootId);
        if (root == null || root.excludeFromBudgets() || root.type() != Category.Type.EXPENSE) {
            return index(byId, ids);
        }
        ids.add(rootId);
        if (budget.isIncludeSubcategories()) {
            Deque<Long> queue = new ArrayDeque<>(children.getOrDefault(rootId, List.of()));
            while (!queue.isEmpty()) {
                Long id = queue.poll();
                CategoryResponse category = byId.get(id);
                if (category == null || category.excludeFromBudgets()) {
                    continue;
                }
                ids.add(id);
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return index(byId, ids);
    }

    private static Map<Long, CategoryResponse> index(Map<Long, CategoryResponse> byId, Set<Long> ids) {
        Map<Long, CategoryResponse> scope = new HashMap<>();
        for (Long id : ids) {
            CategoryResponse category = byId.get(id);
            if (category != null && !category.excludeFromBudgets()) {
                scope.put(id, category);
            }
        }
        return scope;
    }

    private long toBudgetCurrency(long baseMinor, String baseCode, Budget budget, LocalDate onDate) {
        return LedgerFx.convert(fxPort.getIfAvailable(), baseMinor, baseCode, budget.getCurrencyCode(), onDate);
    }

    private Budget requireOwned(Long userId, Long id) {
        return budgets.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_NOT_FOUND, "Budget not found")
                        .withProperty("id", id));
    }

    /** Successive windows of the budget's period type, current one included (history). */
    public List<BudgetProgressResponse.Period> windows(Budget budget, int count) {
        List<BudgetProgressResponse.Period> windows = new ArrayList<>(count);
        LocalDate start = budget.getPeriodStart();
        LocalDate end = budget.getPeriodEnd();
        for (int i = 0; i < count; i++) {
            windows.add(new BudgetProgressResponse.Period(start, end, budget.getPeriodType()));
            if (i + 1 >= count) {
                break;
            }
            long windowDays = ChronoUnit.DAYS.between(start, end) + 1;
            LocalDate nextStart = end.plusDays(1);
            LocalDate nextEnd = switch (budget.getPeriodType()) {
                case WEEKLY -> nextStart.plusDays(6);
                case MONTHLY -> nextStart.withDayOfMonth(nextStart.lengthOfMonth());
                case QUARTERLY -> nextStart.plusMonths(3).withDayOfMonth(1).minusDays(1);
                case YEARLY -> nextStart.withMonth(12).withDayOfMonth(31);
                case CUSTOM -> nextStart.plusDays(windowDays - 1);
            };
            start = nextStart;
            end = nextEnd;
        }
        return windows;
    }
}
