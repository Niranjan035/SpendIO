package com.spendio.goals.service;

import com.spendio.goals.domain.Goal;
import com.spendio.sharedkernel.events.GoalMilestoneEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Milestone crossing detection (ARCHITECTURE.md D.11/K.6): publishes
 * {@link GoalMilestoneEvent} when an increment moves progress <b>across</b> a
 * threshold - the caller supplies the amount before the change, so the crossing
 * itself is the dedupe: re-evaluating a goal that already passed the threshold never
 * fires again, even after a restart (no notification row exists yet; Phase 11's
 * consumer additionally dedupes on {@code goal:{id}:milestone:{pct}}).
 *
 * <p>The notified thresholds are the goal's configured {@code milestone_pct} (default
 * 90) and completion (100).
 */
@Component
public class GoalMilestoneNotifier {

    private static final Logger log = LoggerFactory.getLogger(GoalMilestoneNotifier.class);

    private final ApplicationEventPublisher events;

    public GoalMilestoneNotifier(ApplicationEventPublisher events) {
        this.events = events;
    }

    /**
     * Evaluate both thresholds after a change of {@code goal.currentAmountMinor};
     * {@code previousCurrent} is the value before it.
     */
    public void evaluate(Goal goal, long previousCurrent) {
        long current = goal.getCurrentAmountMinor();
        long target = goal.getTargetAmountMinor();

        short milestonePct = goal.getMilestonePct();
        long milestoneAmount = ceilPct(target, milestonePct);
        if (previousCurrent < milestoneAmount && current >= milestoneAmount) {
            fire(goal, milestonePct);
        }
        if (previousCurrent < target && current >= target) {
            fire(goal, (short) 100);
        }
    }

    /**
     * Explicit completion ({@code /complete}): fires the 100% milestone when the goal
     * really holds its target. A manual complete below target is not a milestone.
     * A rare duplicate with a crossing that already fired carries the same
     * {@code dedupeKey}, which the Phase 11 notification insert swallows.
     */
    public void notifyCompleted(Goal goal) {
        if (goal.hasReachedTarget()) {
            fire(goal, (short) 100);
        }
    }

    /** Milestone amount: ceil(target * pct / 100) in minor units (no float drift). */
    static long ceilPct(long target, short pct) {
        return (target * pct + 99) / 100;
    }

    private void fire(Goal goal, short pct) {
        GoalMilestoneEvent event = new GoalMilestoneEvent(
                goal.getUserId(),
                goal.getId(),
                goal.getName(),
                pct,
                goal.getCurrentAmountMinor(),
                goal.getTargetAmountMinor(),
                goal.getCurrencyCode(),
                GoalMilestoneEvent.dedupeKey(goal.getId(), pct),
                Instant.now());
        events.publishEvent(event);
        log.debug("goal milestone crossed: goal={} pct={}", goal.getId(), pct);
    }
}
