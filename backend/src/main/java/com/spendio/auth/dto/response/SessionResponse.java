package com.spendio.auth.dto.response;

import com.spendio.auth.domain.UserSession;
import java.time.Instant;

/** One row of GET /auth/sessions (ARCHITECTURE.md D.2, E.2). */
public record SessionResponse(
        String id, String deviceLabel, String ipAddress, boolean current, Instant createdAt, Instant lastUsedAt,
        Instant expiresAt) {

    public static SessionResponse from(UserSession session, String currentSessionUuid) {
        return new SessionResponse(
                session.getSessionUuid(),
                session.getDeviceLabel(),
                session.getIpAddress(),
                session.getSessionUuid() != null && session.getSessionUuid().equals(currentSessionUuid),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt());
    }
}
