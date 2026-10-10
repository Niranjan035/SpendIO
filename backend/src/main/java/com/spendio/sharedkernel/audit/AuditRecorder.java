package com.spendio.sharedkernel.audit;

/**
 * Kernel port for appending to the audit trail (ARCHITECTURE.md D.15, B.3).
 *
 * <p>Implemented by {@code com.spendio.audit.service.AuditLogService}. Modules depend
 * on this interface so recording an action never creates an undeclared module edge;
 * the write happens synchronously in the caller's transaction and must never fail
 * the business action it describes.
 */
public interface AuditRecorder {

    void record(AuditEntry entry);
}
