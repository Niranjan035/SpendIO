package com.spendio.budgets.repository;

import com.spendio.budgets.domain.BudgetAlert;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BudgetAlertRepository extends JpaRepository<BudgetAlert, Long>, JpaSpecificationExecutor<BudgetAlert> {

    /** The dedupe probe behind "once per threshold per period" (the unique key is the backstop). */
    boolean existsByBudgetIdAndPeriodStartAndThresholdPct(Long budgetId, LocalDate periodStart, short thresholdPct);

    Optional<BudgetAlert> findByIdAndBudgetIdAndUserId(Long id, Long budgetId, Long userId);

    /** Phase 11 back-link: the notifier pairs an alert's notification with its row. */
    Optional<BudgetAlert> findByUserIdAndBudgetIdAndPeriodStartAndThresholdPct(
            Long userId, Long budgetId, LocalDate periodStart, short thresholdPct);

    List<BudgetAlert> findByBudgetIdAndPeriodStartOrderByThresholdPctAsc(Long budgetId, LocalDate periodStart);
}
