package com.spendio.recurring.notifier;

import com.spendio.notifications.service.NotificationService;
import com.spendio.recurring.domain.RecurringTransaction;
import com.spendio.recurring.repository.RecurringTransactionRepository;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.domain.enums.RecurringStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily scan (ARCHITECTURE.md D.13): active recurring rules with a {@code next_run_date}
 * inside the next {@code recurring-reminder-days} get one "due soon" notification
 * (dedupe key pins the exact occurrence date).
 */
@Component
public class RecurringDueNotifier {

    private static final Logger log = LoggerFactory.getLogger(RecurringDueNotifier.class);

    private final RecurringTransactionRepository rules;
    private final NotificationService notifications;
    private final int reminderDays;

    public RecurringDueNotifier(RecurringTransactionRepository rules, NotificationService notifications,
            @Value("${spendio.recurring.reminder-days:7}") int reminderDays) {
        this.rules = rules;
        this.notifications = notifications;
        this.reminderDays = reminderDays;
    }

    @Scheduled(cron = "${spendio.recurring.reminder-cron:0 0 9 * * *}")
    @Transactional
    public void scheduledRun() {
        run();
    }

    @Transactional
    public int run() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusDays(reminderDays);
        List<RecurringTransaction> due = rules.findByStatusAndNextRunDateBetween(RecurringStatus.ACTIVE, from, to);
        int created = 0;
        for (RecurringTransaction rule : due) {
            notifications.create(
                    rule.getUserId(),
                    NotificationType.RECURRING_DUE,
                    NotificationSeverity.LOW,
                    "Upcoming: " + rule.getDescription(),
                    "\"" + rule.getDescription() + "\" is due on " + rule.getNextRunDate() + ".",
                    new NotificationService.EntityRef("RECURRING", rule.getId()),
                    "/recurring",
                    Map.of("recurringTransactionId", rule.getId(),
                            "scheduledFor", String.valueOf(rule.getNextRunDate()),
                            "amountMinor", rule.getAmountMinor(),
                            "currencyCode", rule.getCurrencyCode()),
                    "due:recurring:" + rule.getId() + ":" + rule.getNextRunDate());
            created++;
        }
        if (created > 0) {
            log.info("recurring due reminders sent: {}", created);
        }
        return created;
    }
}