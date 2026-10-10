package com.spendio.goals.mapper;

import com.spendio.goals.domain.Goal;
import com.spendio.goals.domain.GoalContribution;
import com.spendio.goals.dto.response.GoalContributionResponse;
import com.spendio.goals.dto.response.GoalResponse;
import com.spendio.sharedkernel.money.BaseCurrencyPort;
import com.spendio.sharedkernel.money.FxConversionPort;
import com.spendio.transactions.support.LedgerFx;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Goal to its read DTO, including every derived figure (ARCHITECTURE.md D.11):
 * progress is integer math on minor units ({@code min(100, current*100/target)}),
 * required-per-month spreads the remainder over whole months up to the target date
 * (rounded up, never below one month), and the on-track verdict compares actual
 * progress against a linear pace from creation to target date using overflow-safe
 * cross-multiplication ({@code current * totalDays >= target * elapsedDays}).
 *
 * <p>{@code baseProgressPct} converts both amounts to the user's base currency at
 * today's rate when the goal is denominated elsewhere; without an FX provider it
 * degrades to the plain progress percentage rather than failing the read.
 */
@Component
public class GoalMapper {

    private final BaseCurrencyPort baseCurrency;
    private final ObjectProvider<FxConversionPort> fxPort;

    public GoalMapper(BaseCurrencyPort baseCurrency, ObjectProvider<FxConversionPort> fxPort) {
        this.baseCurrency = baseCurrency;
        this.fxPort = fxPort;
    }

    public GoalResponse toResponse(Goal goal) {
        LocalDate today = LocalDate.now();
        long target = goal.getTargetAmountMinor();
        long current = goal.getCurrentAmountMinor();
        long remaining = Math.max(0, target - current);
        int progressPct = progressPct(current, target);

        LocalDate targetDate = goal.getTargetDate();
        Long daysRemaining = targetDate == null ? null : ChronoUnit.DAYS.between(today, targetDate);

        Long monthlyRequired = null;
        if (targetDate != null && remaining > 0) {
            long months = Math.max(1, ChronoUnit.MONTHS.between(today, targetDate));
            monthlyRequired = (remaining + months - 1) / months;
        }

        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getDescription(),
                target,
                current,
                remaining,
                progressPct,
                goal.getCurrencyCode(),
                baseProgressPct(goal, today, progressPct),
                targetDate,
                daysRemaining,
                goal.getStatus(),
                goal.getLinkedAccountId(),
                goal.getPriority(),
                goal.getIcon(),
                goal.getColor(),
                goal.getCompletedAt(),
                monthlyRequired,
                onTrack(goal, current, target, targetDate, today),
                goal.getVersion(),
                goal.getCreatedAt());
    }

    public GoalContributionResponse toContributionResponse(GoalContribution contribution) {
        return new GoalContributionResponse(
                contribution.getId(),
                contribution.getGoalId(),
                contribution.getAmountMinor(),
                contribution.getContributedOn(),
                contribution.getMethod(),
                contribution.getTransactionId(),
                contribution.getNotes());
    }

    public static int progressPct(long current, long target) {
        if (target <= 0) {
            return 0;
        }
        return (int) Math.min(100L, current * 100 / target);
    }

    private int baseProgressPct(Goal goal, LocalDate today, int progressPct) {
        String base = baseCurrency.baseCurrencyCode(goal.getUserId());
        if (goal.getCurrencyCode().equals(base)) {
            return progressPct;
        }
        FxConversionPort fx = fxPort.getIfAvailable();
        if (fx == null) {
            return progressPct;
        }
        long baseTarget = LedgerFx.convert(fx, goal.getTargetAmountMinor(), goal.getCurrencyCode(), base, today);
        long baseCurrent = LedgerFx.convert(fx, goal.getCurrentAmountMinor(), goal.getCurrencyCode(), base, today);
        return progressPct(baseCurrent, baseTarget);
    }

    /** Null until there is a deadline to be behind; completed goals are on track by definition. */
    private static Boolean onTrack(Goal goal, long current, long target, LocalDate targetDate, LocalDate today) {
        if (targetDate == null || goal.getCreatedAt() == null) {
            return null;
        }
        if (goal.getStatus() == com.spendio.sharedkernel.domain.enums.GoalStatus.COMPLETED) {
            return true;
        }
        LocalDate created = LocalDate.ofInstant(goal.getCreatedAt(), ZoneOffset.UTC);
        long totalDays = ChronoUnit.DAYS.between(created, targetDate);
        if (totalDays <= 0) {
            return current >= target;
        }
        long elapsed = Math.max(0, Math.min(totalDays, ChronoUnit.DAYS.between(created, today)));
        return current * totalDays >= target * elapsed;
    }
}
