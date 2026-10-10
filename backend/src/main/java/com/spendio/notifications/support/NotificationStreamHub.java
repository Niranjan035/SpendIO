package com.spendio.notifications.support;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory SSE fan-out (ARCHITECTURE.md D.13): each connected browser registers one
 * {@link SseEmitter}; {@link #publish} pushes the created notification so the client
 * refreshes immediately instead of waiting on its poll interval. Single-node by
 * design - a multi-instance deployment would swap this for Redis pub/sub.
 */
@Component
public class NotificationStreamHub {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamHub.class);

    private static final long EMITTER_TIMEOUT_MS = 45_000;

    private final Map<Long, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final Set<SseEmitter> idle = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        idle.add(emitter);
        subscribers.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(error -> remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("stream", "notifications")));
        } catch (Exception ex) {
            log.debug("notification stream connect failed for user={}: {}", userId, ex.getMessage());
            remove(userId, emitter);
        }
        return emitter;
    }

    public void publish(Long userId, Object data) {
        Set<SseEmitter> userEmitters = subscribers.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (Exception ex) {
                log.debug("notification stream send failed for user={}: {}", userId, ex.getMessage());
                remove(userId, emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        idle.remove(emitter);
        Set<SseEmitter> userEmitters = subscribers.get(userId);
        if (userEmitters != null) {
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                subscribers.remove(userId, userEmitters);
            }
        }
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // already completed
        }
    }
}