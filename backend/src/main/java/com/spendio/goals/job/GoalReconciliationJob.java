package com.spendio.goals.job;

import com.spendio.goals.domain.Goal;
import com.spendio.goals.repository.GoalContributionRepository;
import com.spendio.goals.repository.GoalRepository;
import com.spendio.goals.service.GoalMilestoneNotifier;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import com.spendio.sharedkernel.domain.job.JobExecutionLog;
import com.spendio.sharedkernel.job.repository.JobExecutionLogRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly cache repair (ARCHITECTURE.md D.11/K.6): recomputes every goal's
 * {@code current_amount_minor} from its contributions and writes it back only when it
 * differs, so a lost update or a killed request can never leave the visible total
 * permanently wrong. A goal whose repaired sum has reached the target is completed in
 * the same pass, and a milestone that repair crossed fires on the way (previous value
 * = the stale cache, so the crossing semantics hold).
 *
 * <p>Idempotent: when nothing drifted, zero rows are written and no event fires.
 */
@Component
public class GoalReconciliationJob {

    public static final String JOB_NAME = "goal-reconciliation";

    private static final Logger log = LoggerFactory.getLogger(GoalReconciliationJob.class);

    private final GoalRepository goals;
    private final GoalContributionRepository contributions;
    private final GoalMilestoneNotifier notifier;
    private final JobExecutionLogRepository jobLog;

    public GoalReconciliationJob(GoalRepository goals, GoalContributionRepository contributions,
            GoalMilestoneNotifier notifier, JobExecutionLogRepository jobLog) {
        this.goals = goals;
        this.contributions = contributions;
        this.notifier = notifier;
        this.jobLog = jobLog;
    }

    @Scheduled(cron = "${spendio.goals.reconciliation-cron:0 45 0 * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    /** Manual/test entry point: repair drifted totals and complete what reached target. */
    @Transactional
    public int run() {
        JobExecutionLog entry = JobExecutionLog.start(JOB_NAME, Instant.now());
        int repaired = 0;
        try {
            List<Goal> active = goals.findByDeletedAtIsNullAndStatusNot(GoalStatus.CANCELLED);
            for (Goal goal : active) {
                long sum = contributions.sumByGoalId(goal.getId());
                if (sum != goal.getCurrentAmountMinor()) {
                    long previous = goal.getCurrentAmountMinor();
                    goals.repairCurrent(goal.getId(), sum);
                    goal.setCurrentAmountMinor(sum);
                    notifier.evaluate(goal, previous);
                    repaired++;
                }
                if (goal.getStatus() == GoalStatus.ACTIVE && sum >= goal.getTargetAmountMinor()) {
                    goals.markCompletedIfReached(goal.getId(), GoalStatus.ACTIVE, GoalStatus.COMPLETED,
                            Instant.now());
                }
            }
            entry.succeed(repaired, "repaired=" + repaired, Instant.now());
        } catch (RuntimeException failure) {
            entry.fail(failure.getMessage(), Instant.now());
            log.warn("goal reconciliation failed: {}", failure.getMessage());
        } finally {
            jobLog.save(entry);
        }
        return repaired;
    }
}
