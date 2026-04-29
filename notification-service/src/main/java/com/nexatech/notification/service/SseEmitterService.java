package com.nexatech.notification.service;

import com.nexatech.notification.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {

    private static final long SSE_TIMEOUT = 300_000L; // 5 minutes

    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Create and register an SseEmitter for the given user.
     * On completion, timeout, or error the emitter is automatically removed from the map.
     */
    public SseEmitter addEmitter(UUID userId) {
        // Remove any previous emitter for this user
        removeEmitter(userId);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            log.debug("SSE emitter completed for userId={}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE emitter timed out for userId={}", userId);
            emitters.remove(userId);
        });

        emitter.onError(ex -> {
            log.debug("SSE emitter error for userId={}: {}", userId, ex.getMessage());
            emitters.remove(userId);
        });

        emitters.put(userId, emitter);
        log.info("SSE emitter registered for userId={}", userId);
        return emitter;
    }

    /**
     * Push a notification event to the user if they have an active SSE connection.
     */
    public void sendToUser(UUID userId, NotificationResponse data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(data));
            log.debug("SSE event pushed to userId={}", userId);
        } catch (IOException | IllegalStateException e) {
            log.debug("Failed to send SSE event to userId={}, removing emitter: {}",
                    userId, e.getMessage());
            removeEmitter(userId);
        }
    }

    /**
     * Remove and complete an emitter for the given user.
     */
    public void removeEmitter(UUID userId) {
        SseEmitter emitter = emitters.remove(userId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // Ignore — emitter may already be completed/timed out
            }
        }
    }
}
