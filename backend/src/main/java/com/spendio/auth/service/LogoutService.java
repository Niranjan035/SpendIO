package com.spendio.auth.service;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.domain.UserSession;
import com.spendio.auth.repository.UserSessionRepository;
import com.spendio.auth.security.TokenCodec;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * POST /auth/logout and /auth/logout-all (ARCHITECTURE.md D.2). Both are idempotent:
 * an absent or already-revoked cookie still ends with a clean 204 and a cleared cookie.
 */
@Service
public class LogoutService {

    private static final Logger log = LoggerFactory.getLogger(LogoutService.class);

    private final UserSessionRepository sessionRepository;
    private final SessionService sessionService;

    public LogoutService(UserSessionRepository sessionRepository, SessionService sessionService) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
    }

    /** Revokes the session named by the refresh cookie; no-op when it is unknown. */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        Optional<UserSession> maybe =
                sessionRepository.findByRefreshTokenHash(TokenCodec.sha256Hex(rawRefreshToken));
        if (maybe.isPresent() && !maybe.get().isRevoked()) {
            UserSession session = maybe.get();
            sessionService.revoke(session, RevocationReason.LOGOUT);
            log.info("security event=LOGOUT user_id={} session={}", session.getUserId(), session.getSessionUuid());
        }
    }

    @Transactional
    public int logoutAll(Long userId) {
        int revoked = sessionService.revokeAllForUser(userId, RevocationReason.LOGOUT);
        log.info("security event=LOGOUT_ALL user_id={} sessions_revoked={}", userId, revoked);
        return revoked;
    }
}
