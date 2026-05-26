package com.nexatech.notification.service.audit;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.notification.event.dto.AuditLogEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fire-and-forget publisher for audit events. Failures are swallowed so audit
 * problems never break business flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventPublisher {

    private static final String SOURCE_SERVICE = "notification-service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AuditActorResolver auditActorResolver;

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
        if (event.getActor() == null) {
            AuditActorResolver.ActorInfo actor = auditActorResolver.resolveFromCurrentRequest();
            event.setActor(actor.displayName());
            if (event.getActorId() == null) {
                event.setActorId(actor.userId());
            }
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

    /** Shorthand for system-initiated events (no actor in scope). */
    public void publishSystem(String action, String entityType, String entityId, String entityLabel,
            String message, Map<String, Object> metadata) {
        publish(AuditLogEvent.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .entityLabel(entityLabel)
                .actor("system")
                .message(message)
                .metadata(metadata)
                .build());
    }
}
