package com.spendio.sharedkernel.events;

import java.time.Instant;

/**
 * Published after an account is soft-deleted (ARCHITECTURE.md D.3, Phase 14).
 *
 * <p>Lives in the kernel so listeners in other modules (session revocation, upload
 * purge, system notification, audit) react without the {@code users} module depending
 * on them (B.3). Carries plain values only.
 */
public record UserAccountDeletedEvent(Long userId, String email, Instant deletedAt) {
}
