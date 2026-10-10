package com.spendio.auth.service;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.domain.UserSession;
import com.spendio.auth.dto.response.AuthResponse;
import com.spendio.auth.dto.response.Authenticated;
import com.spendio.auth.repository.UserSessionRepository;
import com.spendio.auth.security.TokenCodec;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.users.dto.response.LoginStateResponse;
import com.spendio.users.dto.response.UserResponse;
import com.spendio.users.service.UserAccountService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * POST /auth/refresh (ARCHITECTURE.md D.2, I.4): hash the cookie token, load the
 * session, detect reuse (revoked presentation revokes the whole family), reject
 * expired sessions, then rotate - new row in the same family, old row {@code ROTATED}.
 */
@Service
public class TokenRotationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRotationService.class);

    private final UserSessionRepository sessionRepository;
    private final SessionService sessionService;
    private final TokenService tokenService;
    private final UserAccountService userAccountService;

    public TokenRotationService(
            UserSessionRepository sessionRepository,
            SessionService sessionService,
            TokenService tokenService,
            UserAccountService userAccountService) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.tokenService = tokenService;
        this.userAccountService = userAccountService;
    }

    /**
     * Deliberately not {@code @Transactional}: reuse detection revokes the family and
     * then throws - wrapping both in one transaction would roll the revocation back
     * and let the attacker keep using the stolen chain.
     */
    public Authenticated rotate(String rawRefreshToken, String ipAddress, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is missing");
        }
        Optional<UserSession> maybe = sessionRepository.findByRefreshTokenHash(
                TokenCodec.sha256Hex(rawRefreshToken));
        if (maybe.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid");
        }
        UserSession session = maybe.get();
        Instant now = Instant.now();

        if (session.isRevoked()) {
            int revoked = sessionService.revokeFamily(session.getFamilyId(), RevocationReason.REUSE_DETECTED);
            log.warn(
                    "security event=TOKEN_REUSE_DETECTED user_id={} family={} sessions_revoked={}",
                    session.getUserId(),
                    session.getFamilyId(),
                    revoked);
            throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED, "Refresh token reuse detected");
        }
        if (session.isExpired(now)) {
            sessionService.revoke(session, RevocationReason.EXPIRED);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token has expired");
        }

        Optional<LoginStateResponse> account = userAccountService.accountState(session.getUserId());
        if (account.isEmpty()) {
            sessionService.revokeFamily(session.getFamilyId(), RevocationReason.ADMIN);
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid");
        }
        if (!account.get().active()) {
            sessionService.revokeFamily(session.getFamilyId(), RevocationReason.ADMIN);
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Account disabled");
        }

        session.touchLastUsed(now);
        SessionService.IssuedSession issued = sessionService.rotateInto(session, ipAddress, userAgent);
        session.markReplacedBy(issued.session().getId(), now);
        sessionRepository.save(session);

        UserResponse user = userAccountService.summary(session.getUserId());
        log.debug(
                "security event=TOKEN_ROTATED user_id={} session={} replaced={}",
                user.id(),
                issued.session().getSessionUuid(),
                session.getSessionUuid());

        AuthResponse auth = tokenService.issueAuthResponse(user, issued.session());
        return new Authenticated(
                auth,
                issued.rawRefreshToken(),
                Duration.between(issued.session().getIssuedAt(), issued.session().getExpiresAt()));
    }
}
