package com.spendio.sharedkernel.events;

import java.time.Instant;

/**
 * Published after a successful registration (ARCHITECTURE.md D.2, G.4).
 *
 * <p>Lives in the kernel so any module can listen without depending on {@code auth}
 * (B.3). Carries plain values only - no feature types - and is deliberately not
 * Spring's {@code ApplicationEvent}: a future outbox publisher will map it to a
 * transactional event record.
 *
 * <p>Known listeners (later phases): default categories &amp; notification
 * preferences, personal cash account seeding, welcome notification (Phase 11),
 * welcome mail (Phase 2, via the auth registration flow itself).
 */
public record NewUserRegisteredEvent(
        Long userId, String email, String displayName, String locale, String timezone, String baseCurrencyCode,
        Instant registeredAt) {
}
