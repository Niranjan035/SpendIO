package com.spendio.budgets.notifier;

import com.spendio.budgets.domain.BudgetAlert;
import com.spendio.budgets.repository.BudgetAlertRepository;
import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.BudgetAlertTriggeredEvent;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Budget threshold crossing -> notification (ARCHITECTURE.md D.13). The alert insert
 * is the durable event; this consumer re-uses its {@code dedupeKey}, then back-links
 * {@code budget_alerts.notification_id} so the alert UI can jump straight to the
 * notification row.
 */
@Component
public class BudgetAlertNotifier {

    private final NotificationService notifications;
    private final BudgetAlertRepository alerts;

    public BudgetAlertNotifier(NotificationService notifications, BudgetAlertRepository alerts) {
        this.notifications = notifications;
        this.alerts = alerts;
    }

    @EventListener
    @Transactional
    public void on(BudgetAlertTriggeredEvent event) {
        int percent = event.thresholdPct();
        notifications.create(
                event.userId(),
                NotificationType.BUDGET_ALERT,
                percent >= 100 ? NotificationSeverity.HIGH : NotificationSeverity.MEDIUM,
                "Budget \"" + event.budgetName() + "\" crossed " + percent + "%",
                "Spent " + format(event.spentMinor(), event.currencyCode()) + " of "
                        + format(event.budgetAmountMinor(), event.currencyCode()) + " for the period ending "
                        + event.periodEnd() + ".",
                new NotificationService.EntityRef("BUDGET", event.budgetId()),
                "/budgets/" + event.budgetId(),
                Map.of(
                        "budgetId", event.budgetId(),
                        "periodStart", event.periodStart().toString(),
                        "thresholdPct", (int) event.thresholdPct(),
                        "spentMinor", event.spentMinor(),
                        "amountMinor", event.budgetAmountMinor(),
                        "currencyCode", event.currencyCode()),
                event.dedupeKey())
                .ifPresent(notification -> alerts
                        .findByUserIdAndBudgetIdAndPeriodStartAndThresholdPct(
                                event.userId(), event.budgetId(), event.periodStart(), event.thresholdPct())
                        .ifPresent(alert -> link(alert, notification.getId())));
    }

    private void link(BudgetAlert alert, Long notificationId) {
        alert.linkNotification(notificationId);
        alerts.save(alert);
    }

    private static String format(long amountMinor, String currencyCode) {
        return (currencyCode + " " + amountMinor / 100.0).replaceAll("\\.0([0-9])$", ".$1");
    }
}