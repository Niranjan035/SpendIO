package com.spendio.audit.service;

import com.spendio.audit.domain.AuditLog;
import com.spendio.audit.dto.AuditLogResponse;
import com.spendio.audit.repository.AuditLogRepository;
import com.spendio.sharedkernel.audit.AuditEntry;
import com.spendio.sharedkernel.audit.AuditRecorder;
import com.spendio.sharedkernel.web.PageRequestFactory;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The single audit write path (ARCHITECTURE.md D.15): appends the entry with the
 * request's address, user agent and trace id, and answers the viewer's paginated
 * reads. Recording is best-effort - a failed write is logged and swallowed so an
 * audit failure never rolls back the business action it describes.
 */
@Service
public class AuditLogService implements AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLog;

    public AuditLogService(AuditLogRepository auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    @Transactional
    public void record(AuditEntry entry) {
        try {
            HttpServletRequest request = currentRequest();
            AuditLog row = AuditLog.of(
                    entry.userId(),
                    entry.actorType(),
                    entry.action(),
                    entry.entityType(),
                    entry.entityId(),
                    entry.before(),
                    entry.after(),
                    request == null ? null : clientIp(request),
                    request == null ? null : truncate(request.getHeader("User-Agent"), 255),
                    truncate(MDC.get("requestId"), 32),
                    Instant.now());
            auditLog.save(row);
        } catch (RuntimeException failure) {
            log.warn("audit record failed: action={} user={} error={}",
                    entry.action(), entry.userId(), failure.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(Long userId, Integer page, Integer size) {
        Pageable pageable = PageRequestFactory.of(page, size);
        Page<AuditLog> result = auditLog.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(result.map(AuditLogResponse::from));
    }

    /** Detaches a deleted user's rows (C.4): the trail survives, the identity does not. */
    @Transactional
    public int anonymiseForUser(Long userId) {
        return auditLog.detachUser(userId);
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return auditLog.deleteOlderThan(cutoff);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return truncate(comma >= 0 ? forwarded.substring(0, comma) : forwarded, 45);
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
