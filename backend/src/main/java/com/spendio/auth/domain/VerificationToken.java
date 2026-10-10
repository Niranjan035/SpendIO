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
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Email verification token (ARCHITECTURE.md D.2): SHA-256 at rest, 24-hour expiry,
 * one-shot consumption; {@code payload_json} reserves room for address changes.
 */
@Entity
@Table(
        name = "verification_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_verification_hash", columnNames = "token_hash"))
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ENUM)
    @Column(name = "purpose", nullable = false)
    private TokenPurpose purpose;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationToken() {
    }

    public static VerificationToken issue(
            Long userId, TokenPurpose purpose, String tokenHash, Instant expiresAt, String payloadJson) {
        VerificationToken token = new VerificationToken();
        token.userId = userId;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.payloadJson = payloadJson;
        token.createdAt = Instant.now();
        return token;
    }

    public void consume(Instant at) {
        this.consumedAt = at;
    }

    public boolean isUsable(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
