package com.spendio.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One refresh-token session row (ARCHITECTURE.md D.2, I.4).
 *
 * <p>Rotation creates a new row in the same {@code familyId}; presenting a revoked row
 * is the reuse signal that kills the whole family. Deliberately not extending
 * {@code BaseEntity}: the V2 table has no {@code version}/{@code updated_at}.
 */
@Entity
@Table(
        name = "user_sessions",
        uniqueConstraints = @UniqueConstraint(name = "uk_sessions_hash", columnNames = "refresh_token_hash"))
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "session_uuid", length = 36, nullable = false)
    private String sessionUuid;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "refresh_token_hash", length = 64, nullable = false)
    private String refreshTokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "revocation_reason")
    private RevocationReason revocationReason;

    @Column(name = "replaced_by_session_id")
    private Long replacedBySessionId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "device_label", length = 80)
    private String deviceLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserSession() {
    }

    public static UserSession issue(
            Long userId,
            String sessionUuid,
            String familyId,
            String refreshTokenHash,
            Instant issuedAt,
            Duration lifetime,
            String ipAddress,
            String userAgent,
            String deviceLabel) {
        UserSession session = new UserSession();
        session.userId = userId;
        session.sessionUuid = sessionUuid;
        session.familyId = familyId;
        session.refreshTokenHash = refreshTokenHash;
        session.issuedAt = issuedAt;
        session.expiresAt = issuedAt.plus(lifetime);
        session.ipAddress = ipAddress;
        session.userAgent = userAgent;
        session.deviceLabel = deviceLabel;
        session.createdAt = issuedAt;
        return session;
    }

    public void revoke(RevocationReason reason, Instant at) {
        this.revokedAt = at;
        this.revocationReason = reason;
    }

    public void markReplacedBy(Long newSessionId, Instant at) {
        revoke(RevocationReason.ROTATED, at);
        this.replacedBySessionId = newSessionId;
    }

    public void touchLastUsed(Instant at) {
        this.lastUsedAt = at;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return expiresAt.isBefore(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSessionUuid() {
        return sessionUuid;
    }

    public String getFamilyId() {
        return familyId;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RevocationReason getRevocationReason() {
        return revocationReason;
    }

    public Long getReplacedBySessionId() {
        return replacedBySessionId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
