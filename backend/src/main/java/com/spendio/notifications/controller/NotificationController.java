package com.spendio.notifications.controller;

import com.spendio.notifications.dto.request.MarkReadRequest;
import com.spendio.notifications.dto.request.UpdateNotificationPreferenceRequest;
import com.spendio.notifications.dto.response.NotificationPreferenceResponse;
import com.spendio.notifications.dto.response.NotificationResponse;
import com.spendio.notifications.dto.response.UnreadCountResponse;
import com.spendio.notifications.service.NotificationPreferenceService;
import com.spendio.notifications.service.NotificationService;
import com.spendio.notifications.support.NotificationStreamHub;
import com.spendio.sharedkernel.domain.enums.NotificationSeverity;
import com.spendio.sharedkernel.domain.enums.NotificationType;
import com.spendio.sharedkernel.web.CurrentUserId;
import com.spendio.sharedkernel.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Notification endpoints (ARCHITECTURE.md E.13): the paged inbox, unread counts, the
 * SSE live stream, read/dismiss/delete and per-type preferences.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;
    private final NotificationPreferenceService preferences;
    private final NotificationStreamHub stream;

    public NotificationController(NotificationService service, NotificationPreferenceService preferences,
            NotificationStreamHub stream) {
        this.service = service;
        this.preferences = preferences;
        this.stream = stream;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationSeverity severity,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.list(userId, unreadOnly, type, severity, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@CurrentUserId Long userId) {
        return service.unreadCount(userId);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUserId Long userId) {
        return stream.subscribe(userId);
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(@CurrentUserId Long userId, @PathVariable Long id) {
        return service.markRead(userId, id);
    }

    @PostMapping("/read-all")
    public UnreadCountResponse markAllRead(@CurrentUserId Long userId,
            @RequestBody(required = false) MarkReadRequest request) {
        int marked = service.markAllRead(userId);
        return new UnreadCountResponse(marked, java.util.Map.of());
    }

    @PostMapping("/{id}/dismiss")
    public void dismiss(@CurrentUserId Long userId, @PathVariable Long id) {
        service.dismiss(userId, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@CurrentUserId Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }

    @GetMapping("/preferences")
    public NotificationPreferenceResponse preferences(@CurrentUserId Long userId) {
        return preferences.list(userId);
    }

    @PutMapping("/preferences")
    public NotificationPreferenceResponse updatePreferences(@CurrentUserId Long userId,
            @Valid @RequestBody UpdateNotificationPreferenceRequest request) {
        return preferences.update(userId, request);
    }
}