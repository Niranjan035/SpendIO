package com.spendio.notifications.notifier;

import com.spendio.notifications.service.NotificationService;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.events.ImportCompletedEvent;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Import execution finished (ARCHITECTURE.md D.13). A fully failed import surfaces as
 * {@code IMPORT_FAILED} so the user can fix and retry; partial success stays a
 * {@code COMPLETED} notice with the tallies.
 */
@Component
public class ImportCompletionNotifier {

    private final NotificationService notifications;

    public ImportCompletionNotifier(NotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener
    public void on(ImportCompletedEvent event) {
        boolean failed = event.createdRows() == 0 && event.failedRows() > 0;
        NotificationType type = failed ? NotificationType.IMPORT_FAILED : NotificationType.IMPORT_COMPLETED;
        notifications.create(
                event.userId(),
                type,
                failed ? NotificationSeverity.HIGH : NotificationSeverity.MEDIUM,
                failed ? "Import failed: " + event.originalFilename()
                        : "Import finished: " + event.originalFilename(),
                failed
                        ? "None of " + event.totalRows() + " rows could be imported - review the error report."
                        : event.createdRows() + " of " + event.totalRows() + " rows imported"
                                + (event.failedRows() > 0 ? ", " + event.failedRows() + " failed." : "."),
                new NotificationService.EntityRef("IMPORT", event.importId()),
                "/import/history",
                Map.of("importId", event.importId(), "createdRows", event.createdRows(),
                        "failedRows", event.failedRows(), "totalRows", event.totalRows()),
                event.dedupeKey());
    }
}