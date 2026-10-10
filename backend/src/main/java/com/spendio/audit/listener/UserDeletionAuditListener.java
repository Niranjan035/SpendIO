package com.spendio.audit.listener;

import com.spendio.audit.service.AuditLogService;
import com.spendio.sharedkernel.events.UserAccountDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account deletion cleanup (ARCHITECTURE.md D.3, C.4): the audit trail survives the
 * account but is detached from the personal identifier. Reacts to the kernel event so
 * the {@code users} module never depends on {@code audit} (B.3).
 */
@Component
public class UserDeletionAuditListener {

    private final AuditLogService auditLog;

    public UserDeletionAuditListener(AuditLogService auditLog) {
        this.auditLog = auditLog;
    }

    @EventListener
    @Transactional
    public void on(UserAccountDeletedEvent event) {
        auditLog.anonymiseForUser(event.userId());
    }
}
