package com.spendio.budgets.service;

import com.spendio.budgets.domain.Budget;
import com.spendio.budgets.domain.BudgetAlert;
import com.spendio.budgets.dto.response.BudgetProgressResponse;
import com.spendio.budgets.repository.BudgetAlertRepository;
import com.spendio.budgets.repository.BudgetRepository;
import com.spendio.sharedkernel.events.BudgetAlertTriggeredEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Threshold alerts (ARCHITECTURE.md D.10, K.5): utilisation is compared with exact
 * integer maths ({@code spent * 100 >= threshold * allowance}) so rounding can never
 * decide whether a threshold was crossed. Both the user's configured threshold and the
 * 100% over-budget line are checked on every evaluation.
 *
 * <p>Exactly-once is two-layered: the {@code existsBy...} probe keeps the common path
 * quiet, and the insert runs in its own transaction so losing the unique-key race to a
 * concurrent evaluation aborts only that single insert - never the batch. Each
 * successful insert publishes {@link BudgetAlertTriggeredEvent} for Phase 11.
 */
@Service
public class BudgetAlertService {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);

    private final BudgetRepository budgets;
    private final BudgetAlertRepository alerts;
    private final BudgetProgressService progress;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate insertTx;

    public BudgetAlertService(BudgetRepository budgets, BudgetAlertRepository alerts,
            BudgetProgressService progress, ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.budgets = budgets;
        this.alerts = alerts;
        this.progress = progress;
        this.events = events;
        this.insertTx = new TransactionTemplate(transactionManager);
        this.insertTx.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW");
    }

    /**
     * Evaluate every active, alert-enabled budget of {@code userId} whose window
     * overlaps {@code [from, to]}. Returns how many alerts were newly created.
     */
    public int evaluate(Long userId, LocalDate from, LocalDate to) {
        List<Budget> candidates = budgets.findAlertCandidates(userId, Budget.Status.ACTIVE, from, to);
        int created = 0;
        for (Budget budget : candidates) {
            created += evaluate(budget);
        }
        return created;
    }

    /** One budget: both thresholds, each at most once per period. */
    public int evaluate(Budget budget) {
        BudgetProgressResponse snapshot = progress.compute(
                budget.getUserId(), budget, budget.getPeriodStart(), budget.getPeriodEnd());
        long spent = snapshot.spentMinor();
        long allowance = snapshot.allowanceMinor();
        if (allowance <= 0) {
            return 0;
        }

        int created = 0;
        for (short threshold : new short[] {budget.getAlertThresholdPct(), 100}) {
            if (spent * 100L < (long) threshold * allowance) {
                continue;
            }
            if (alerts.existsByBudgetIdAndPeriodStartAndThresholdPct(
                    budget.getId(), budget.getPeriodStart(), threshold)) {
                continue;
            }
            if (insertAlert(budget, threshold, spent, allowance)) {
                created++;
                events.publishEvent(new BudgetAlertTriggeredEvent(
                        budget.getUserId(),
                        budget.getId(),
                        budget.getName(),
                        budget.getPeriodStart(),
                        budget.getPeriodEnd(),
                        threshold,
                        spent,
                        allowance,
                        budget.getCurrencyCode(),
                        BudgetAlertTriggeredEvent.dedupeKey(budget.getId(), budget.getPeriodStart(), threshold),
                        Instant.now()));
            }
        }
        return created;
    }

    /** Isolated insert: a duplicate-key loss rolls back alone and reads as "already alerted". */
    private boolean insertAlert(Budget budget, short threshold, long spent, long allowance) {
        try {
            return Boolean.TRUE.equals(insertTx.execute(status -> {
                alerts.saveAndFlush(BudgetAlert.trigger(
                        budget.getId(),
                        budget.getUserId(),
                        budget.getPeriodStart(),
                        budget.getPeriodEnd(),
                        threshold,
                        allowance,
                        spent,
                        Instant.now()));
                return true;
            }));
        } catch (DataIntegrityViolationException race) {
            log.debug("budget alert already recorded: budget={} threshold={}", budget.getId(), threshold);
            return false;
        }
    }
}
