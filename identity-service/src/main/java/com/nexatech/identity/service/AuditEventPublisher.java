package com.nexatech.identity.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.identity.dto.event.AuditLogEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fire-and-forget publisher for audit events.
 * Failures are swallowed so audit problems never break business flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventPublisher {

    private static final String SOURCE_SERVICE = "identity-service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topics.audit-log:audit-topic}")
    private String auditTopic;

    public void publish(AuditLogEvent event) {
        if (event == null) {
            return;
        }
        if (event.getSourceService() == null) {
            event.setSourceService(SOURCE_SERVICE);
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now().toEpochMilli());
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = event.getEntityId() != null ? event.getEntityId() : event.getActor();
            kafkaTemplate.send(auditTopic, key, payload);
            log.debug("Published audit event action={} entity={}/{} actor={}",
                    event.getAction(), event.getEntityType(), event.getEntityId(), event.getActor());
        } catch (Exception ex) {
            log.warn("Failed to publish audit event action={} entity={}/{}: {}",
                    event.getAction(), event.getEntityType(), event.getEntityId(), ex.getMessage());
        }
    }

    /**
     * Convenience for actions where the actor IS the entity (typical for auth flows:
     * register / login / logout / change-password). The same identity is used for both
     * {@code actor} (display) and {@code entityLabel}, and the same UUID is used for both
     * {@code actorId} and {@code entityId}.
     */
    public void publishUserAction(
            String action, String userId, String username, String email, String message) {
        String display = pickDisplay(username, email, userId);
        publish(AuditLogEvent.builder()
                .action(action)
                .entityType("User")
                .entityId(userId)
                .entityLabel(display)
                .actor(display)
                .actorId(userId)
                .message(message)
                .build());
    }

    /** With metadata (e.g. provider, IP, userAgent). */
    public void publishUserAction(
            String action, String userId, String username, String email, String message,
            Map<String, Object> metadata) {
        String display = pickDisplay(username, email, userId);
        publish(AuditLogEvent.builder()
                .action(action)
                .entityType("User")
                .entityId(userId)
                .entityLabel(display)
                .actor(display)
                .actorId(userId)
                .message(message)
                .metadata(metadata)
                .build());
    }

    private static String pickDisplay(String username, String email, String fallback) {
        if (username != null && !username.isBlank()) {
            return username;
        }
        if (email != null && !email.isBlank()) {
            return email;
        }
        return fallback != null ? fallback : "system";
    }
}
