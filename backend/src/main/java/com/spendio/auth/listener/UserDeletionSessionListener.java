package com.spendio.auth.listener;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.service.SessionService;
import com.spendio.sharedkernel.events.UserAccountDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes every session when an account is deleted (ARCHITECTURE.md D.2, Phase 14).
 * Reacts to the kernel event so the {@code users} module never depends on {@code auth}.
 */
@Component
public class UserDeletionSessionListener {

    private final SessionService sessionService;

    public UserDeletionSessionListener(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @EventListener
    @Transactional
    public void on(UserAccountDeletedEvent event) {
        sessionService.revokeAllForUser(event.userId(), RevocationReason.ACCOUNT_DELETED);
    }
}
