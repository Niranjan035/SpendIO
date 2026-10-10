package com.spendio.notifications.notifier;

import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.RecurringRunFailedEvent;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A recurring occurrence that failed to post (ARCHITECTURE.md D.13). Published inside
 * the failure-bookkeeping transaction, so exactly one notification per failed date -
 * the {@code dedupeKey} stops repeated attempts on the same date from stacking.
 */
@Component
public class RecurringFailedNotifier {

    private final NotificationService notifications;

    public RecurringFailedNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    public void on(RecurringRunFailedEvent event) {
        notifications.create(
                event.userId(),
                NotificationType.RECURRING_FAILED,
                NotificationSeverity.HIGH,
                "Recurring transaction failed: " + event.ruleName(),
                "\"" + event.ruleName() + "\" could not post for "
                        + event.scheduledFor() + ": " + event.message() + ".",
                new NotificationService.EntityRef("RECURRING", event.recurringTransactionId()),
                "/recurring",
                Map.of("recurringTransactionId", event.recurringTransactionId(),
                        "scheduledFor", String.valueOf(event.scheduledFor())),
                event.dedupeKey());
    }
}