package com.spendio.sharedkernel.events;

import java.time.Instant;

/**
 * Published after the account password changes (ARCHITECTURE.md D.2, Phase 14).
 *
 * <p>Lives in the kernel so the notifications module can raise a security alert without
 * the {@code auth} module depending on it (B.3). Carries plain values only.
 */
public record PasswordChangedEvent(Long userId, Instant changedAt) {
}
