package com.spendio.sharedkernel.domain.enums;

/**
 * Goal lifecycle (ARCHITECTURE.md C.3 #16): {@code COMPLETED} requires
 * {@code completed_at} (DB CHECK), so completion always goes through the service
 * methods that set both together.
 */
public enum GoalStatus {
    ACTIVE,
    COMPLETED,
    PAUSED,
    CANCELLED
}
