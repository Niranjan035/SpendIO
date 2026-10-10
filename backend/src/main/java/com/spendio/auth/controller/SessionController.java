package com.spendio.auth.controller;

import com.spendio.auth.domain.RevocationReason;
import com.spendio.auth.domain.UserSession;
import com.spendio.auth.dto.response.SessionResponse;
import com.spendio.auth.service.SessionService;
import com.spendio.sharedkernel.audit.AuditEntry;
import com.spendio.sharedkernel.audit.AuditRecorder;
import com.spendio.sharedkernel.error.BusinessException;
import com.spendio.sharedkernel.error.ErrorCode;
import com.spendio.sharedkernel.security.RefreshCookieService;
import com.spendio.sharedkernel.security.SecurityUtils;
import com.spendio.sharedkernel.security.UserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session visibility and per-session revocation (ARCHITECTURE.md E.2, K.1).
 * A session id belonging to another user answers 404 - never 403 (E.16).
 */
@RestController
@RequestMapping("/api/v1/auth/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final RefreshCookieService refreshCookieService;
    private final AuditRecorder auditRecorder;

    public SessionController(SessionService sessionService, RefreshCookieService refreshCookieService,
            AuditRecorder auditRecorder) {
        this.sessionService = sessionService;
        this.refreshCookieService = refreshCookieService;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping
    public List<SessionResponse> list() {
        UserPrincipal principal = requirePrincipal();
        String current = principal.sessionId() == null ? null : principal.sessionId().toString();
        return sessionService.activeSessions(principal.userId()).stream()
                .map(session -> SessionResponse.from(session, current))
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(@PathVariable String sessionId, HttpServletResponse response) {
        UserPrincipal principal = requirePrincipal();
        UserSession session = sessionService.ownedSession(principal.userId(), sessionId);
        boolean isCurrent = session.getSessionUuid().equals(
                principal.sessionId() == null ? null : principal.sessionId().toString());
        if (!session.isRevoked()) {
            sessionService.revoke(session, RevocationReason.LOGOUT);
        }
        auditRecorder.record(AuditEntry.user(principal.userId(), "SESSION_REVOKED", "SESSION", session.getId()));
        if (isCurrent) {
            refreshCookieService.clear(response);
        }
        return ResponseEntity.noContent().build();
    }

    private static UserPrincipal requirePrincipal() {
        return SecurityUtils.currentPrincipal()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_TOKEN_MISSING, "Authentication is required"));
    }
}
