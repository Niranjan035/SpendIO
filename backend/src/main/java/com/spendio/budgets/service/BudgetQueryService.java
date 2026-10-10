package com.spendio.budgets.service;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.domain.BudgetAlert;
import com.spendio.budgets.dto.response.BudgetAlertResponse;
import com.spendio.budgets.dto.response.BudgetHistoryResponse;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.dto.response.BudgetResponse;
import com.spendio.budgets.mapper.BudgetMapper;
import com.spendio.budgets.repository.BudgetAlertRepository;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.budgets.spec.BudgetFilter;
import com.spendio.budgets.spec.BudgetSpecifications;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.web.PageRequestFactory;
import com.spendio.sharedkernel.web.PageResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget reads (ARCHITECTURE.md D.10/E.10): filtered list, detail, the alert inbox
 * with acknowledgement, and the history roll-up - successive windows of the budget's
 * period type recomputed live from the ledger, so history always reflects the ledger
 * as it stands today.
 */
@Service
public class BudgetQueryService {

    private static final int HISTORY_WINDOWS = 12;

    private final BudgetRepository budgets;
    private final BudgetAlertRepository alerts;
    private final BudgetProgressService progress;
    private final BudgetMapper mapper;

    public BudgetQueryService(BudgetRepository budgets, BudgetAlertRepository alerts,
            BudgetProgressService progress, BudgetMapper mapper) {
        this.budgets = budgets;
        this.alerts = alerts;
        this.progress = progress;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<BudgetResponse> list(Long userId, BudgetFilter filter, Integer page, Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size,
                Sort.by(Sort.Direction.DESC, "periodStart").and(Sort.by(Sort.Direction.ASC, "id")));
        Page<Budget> result = budgets.findAll(
                BudgetSpecifications.forFilter(userId, filter).and(BudgetSpecifications.withCategory()),
                pageable);
        return PageResponse.from(result.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public BudgetResponse detail(Long userId, Long id) {
        return mapper.toResponse(requireOwned(userId, id));
    }

    /** Utilisation for the current window plus the preceding ones (newest last). */
    @Transactional(readOnly = true)
    public BudgetHistoryResponse history(Long userId, Long id) {
        Budget budget = requireOwned(userId, id);
        List<BudgetHistoryResponse.PeriodSlice> periods = progress.windows(budget, HISTORY_WINDOWS).stream()
                .map(window -> {
                    BudgetProgressResponse snapshot =
                            progress.compute(userId, budget, window.start(), window.end());
                    return new BudgetHistoryResponse.PeriodSlice(
                            window.start(),
                            window.end(),
                            snapshot.allowanceMinor(),
                            snapshot.spentMinor(),
                            snapshot.utilisationPct(),
                            snapshot.overBudget(),
                            window.start().equals(budget.getPeriodStart())
                                    && window.end().equals(budget.getPeriodEnd()));
                })
                .toList();
        return new BudgetHistoryResponse(budget.getId(), periods);
    }

    @Transactional(readOnly = true)
    public PageResponse<BudgetAlertResponse> alertList(Long userId, Boolean unacknowledgedOnly,
            LocalDate from, LocalDate to, Integer page, Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size,
                Sort.by(Sort.Direction.DESC, "triggeredAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<BudgetAlert> result = alerts.findAll(alertSpec(userId, unacknowledgedOnly, from, to), pageable);
        Map<Long, Budget> budgetsById = budgetsByIds(result.getContent());
        return PageResponse.from(result.map(alert -> {
            Budget budget = budgetsById.get(alert.getBudgetId());
            return mapper.toAlertResponse(
                    alert, budget == null ? "Budget" : budget.getName(),
                    budget == null ? null : budget.getCurrencyCode());
        }));
    }

    @Transactional
    public BudgetAlertResponse acknowledge(Long userId, Long budgetId, Long alertId) {
        requireOwned(userId, budgetId);
        BudgetAlert alert = alerts.findByIdAndBudgetIdAndUserId(alertId, budgetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_NOT_FOUND, "Budget alert not found")
                        .withProperty("alertId", alertId));
        if (alert.getAcknowledgedAt() == null) {
            alert.acknowledge(Instant.now());
        }
        Budget budget = budgets.findByIdAndUserIdAndDeletedAtIsNull(alert.getBudgetId(), alert.getUserId())
                .orElse(null);
        return mapper.toAlertResponse(alert,
                budget == null ? "Budget" : budget.getName(),
                budget == null ? null : budget.getCurrencyCode());
    }

    private Map<Long, Budget> budgetsByIds(List<BudgetAlert> content) {
        List<Long> ids = content.stream().map(BudgetAlert::getBudgetId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return budgets.findAllById(ids).stream()
                .collect(Collectors.toMap(Budget::getId, Function.identity(), (first, second) -> first));
    }

    private static Specification<BudgetAlert> alertSpec(Long userId, Boolean unacknowledgedOnly,
            LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (Boolean.TRUE.equals(unacknowledgedOnly)) {
                predicates.add(cb.isNull(root.get("acknowledgedAt")));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("triggeredAt"), from.atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            if (to != null) {
                predicates.add(cb.lessThan(
                        root.get("triggeredAt"), to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Budget requireOwned(Long userId, Long id) {
        return budgets.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_NOT_FOUND, "Budget not found")
                        .withProperty("id", id));
    }
}
