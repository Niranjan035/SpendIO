package com.spendio.sharedkernel.domain.enums;

/**
 * Notification categories (ARCHITECTURE.md D.13, K.10): one per notifier source so
 * per-type preferences can veto a whole class of alerts. Values are stable API
 * contract - appended to, never renamed.
 */
public enum NotificationType {
    BUDGET_ALERT,
    GOAL_MILESTONE,
    GOAL_REMINDER,
    RECURRING_DUE,
    RECURRING_FAILED,
    IMPORT_COMPLETED,
    IMPORT_FAILED,
    SYSTEM
}