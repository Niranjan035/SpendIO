package com.spendio.auth.service;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.domain.UserSession;
import com.spendio.auth.repository.UserSessionRepository;
import com.spendio.auth.security.TokenCodec;
import com.spendio.config.SecurityProperties;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session row lifecycle (ARCHITECTURE.md D.2): minting, revocation, family handling.
 *
 * <p>The refresh TTL is encoded in the row itself ({@code expires_at - issued_at}),
 * so rotation preserves the original remember-device window without an extra column
 * (the V2 table is frozen).
 */
@Service
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final SecurityProperties properties;

    public SessionService(UserSessionRepository sessionRepository, SecurityProperties properties) {
        this.sessionRepository = sessionRepository;
        this.properties = properties;
    }

    /**
     * Owner-checked lookup (ARCHITECTURE.md E.16): an unknown id and someone else's id
     * both answer {@code SESSION_NOT_FOUND} - never a 403 that would leak existence.
     */
    public UserSession ownedSession(Long userId, String sessionUuid) {
        return sessionRepository
                .findBySessionUuid(sessionUuid)
                .filter(session -> session.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND, "Session not found"));
    }

    /** A freshly minted session plus the raw (never persisted) refresh token. */
    public record IssuedSession(UserSession session, String rawRefreshToken) {}

    @Transactional
    public IssuedSession createSession(Long userId, boolean rememberDevice, String ipAddress, String userAgent) {
        Duration ttl = rememberDevice
                ? properties.jwt().rememberMeRefreshTtl()
                : properties.jwt().refreshTokenTtl();
        return mint(userId, UUID.randomUUID().toString(), ttl, ipAddress, userAgent);
    }

    /** Rotation keeps the family and the original TTL window (rememberDevice semantics). */
    @Transactional
    public IssuedSession rotateInto(UserSession previous, String ipAddress, String userAgent) {
        Duration window = Duration.between(previous.getIssuedAt(), previous.getExpiresAt());
        if (window.isNegative() || window.isZero()) {
            window = properties.jwt().refreshTokenTtl();
        }
        return mint(previous.getUserId(), previous.getFamilyId(), window, ipAddress, userAgent);
    }

    private IssuedSession mint(Long userId, String familyId, Duration ttl, String ipAddress, String userAgent) {
        String raw = TokenCodec.generate();
        UserSession session = UserSession.issue(
                userId,
                UUID.randomUUID().toString(),
                familyId,
                TokenCodec.sha256Hex(raw),
                Instant.now(),
                ttl,
                ipAddress,
                truncate(userAgent, 255),
                DeviceLabel.from(userAgent));
        return new IssuedSession(sessionRepository.save(session), raw);
    }

    @Transactional(readOnly = true)
    public List<UserSession> activeSessions(Long userId) {
        return sessionRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(
                userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public long activeSessionCount(Long userId) {
        return sessionRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, Instant.now());
    }

    @Transactional
    public void revoke(UserSession session, RevocationReason reason) {
        session.revoke(reason, Instant.now());
        sessionRepository.save(session);
    }

    @Transactional
    public int revokeFamily(String familyId, RevocationReason reason) {
        return sessionRepository.revokeFamily(familyId, reason, Instant.now());
    }

    @Transactional
    public int revokeAllForUser(Long userId, RevocationReason reason) {
        return sessionRepository.revokeAllForUser(userId, reason, Instant.now());
    }

    @Transactional
    public int revokeAllExcept(Long userId, String keepSessionUuid, RevocationReason reason) {
        return sessionRepository.revokeAllForUserExcept(userId, keepSessionUuid, reason, Instant.now());
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
