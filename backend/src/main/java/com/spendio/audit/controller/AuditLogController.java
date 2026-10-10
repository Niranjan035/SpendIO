package com.spendio.audit.controller;

import com.spendio.audit.dto.AuditLogResponse;
import com.spendio.audit.service.AuditLogService;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit-log viewer (ARCHITECTURE.md E.3, Phase 14). Scoped to the authenticated user;
 * other users' rows are never addressable (E.16).
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @CurrentUserId Long userId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer size) {
        return auditLogService.list(userId, page, size);
    }
}
