package com.spendio.goals.notifier;

import com.spendio.goals.domain.Goal;
import com.spendio.goals.repository.GoalRepository;
import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.GoalStatus;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily scan (ARCHITECTURE.md D.13): goals whose {@code target_date} falls within the
 * next {@code goal-reminder-days} get one reminder each (dedupe key includes the
 * target date, so the reminder is not repeated every morning).
 */
@Component
public class GoalReminderNotifier {

    private static final Logger log = LoggerFactory.getLogger(GoalReminderNotifier.class);

    private final GoalRepository goals;
    private final NotificationService notifications;
    private final int reminderDays;

    public GoalReminderNotifier(GoalRepository goals, NotificationService notifications,
            @Value("${spendio.goals.reminder-days:14}") int reminderDays) {
        this.goals = goals;
        this.notifications = notifications;
        this.reminderDays = reminderDays;
    }

    @Scheduled(cron = "${spendio.goals.reminder-cron:0 0 8 * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    @Transactional
    public int run() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusDays(reminderDays);
        List<Goal> due = goals.findByStatusAndTargetDateBetween(GoalStatus.ACTIVE, from, to);
        int created = 0;
        for (Goal goal : due) {
            if (goal.hasReachedTarget()) {
                continue;
            }
            notifications.create(
                    goal.getUserId(),
                    NotificationType.GOAL_REMINDER,
                    NotificationSeverity.LOW,
                    "Goal due soon: " + goal.getName(),
                    "\"" + goal.getName() + "\" has a target date of " + goal.getTargetDate() + ".",
                    new NotificationService.EntityRef("GOAL", goal.getId()),
                    "/goals/" + goal.getId(),
                    java.util.Map.of("goalId", goal.getId(), "targetDate", String.valueOf(goal.getTargetDate())),
                    "reminder:goal:" + goal.getId() + ":" + goal.getTargetDate());
            created++;
        }
        if (created > 0) {
            log.info("goal reminders sent: {}", created);
        }
        return created;
    }
}