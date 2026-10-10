package com.spendio.notifications.notifier;

import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.GoalMilestoneEvent;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Goal milestone / completion -> notification (ARCHITECTURE.md D.13, K.10). The event
 * is published only by actual crossings, and its {@code dedupeKey} is the second gate.
 */
@Component
public class GoalMilestoneNotificationListener {

    private final NotificationService notifications;

    public GoalMilestoneNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    public void on(GoalMilestoneEvent event) {
        boolean completed = event.milestonePct() == 100;
        notifications.create(
                event.userId(),
                NotificationType.GOAL_MILESTONE,
                completed ? NotificationSeverity.HIGH : NotificationSeverity.MEDIUM,
                completed ? "Goal completed: " + event.goalName()
                        : "Goal milestone reached: " + event.goalName(),
                completed
                        ? "You reached your target for \"" + event.goalName() + "\"."
                        : "\"" + event.goalName() + "\" passed " + event.milestonePct()
                                + "% of its target.",
                new NotificationService.EntityRef("GOAL", event.goalId()),
                "/goals/" + event.goalId(),
                Map.of(
                        "goalId", event.goalId(),
                        "milestonePct", (int) event.milestonePct(),
                        "currentMinor", event.currentAmountMinor(),
                        "targetMinor", event.targetAmountMinor(),
                        "currencyCode", event.currencyCode()),
                event.dedupeKey());
    }
}