package com.nexatech.audit.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.audit.dto.event.AuditLogEvent;
import com.nexatech.audit.service.AuditLogService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens to audit envelopes from Kafka and persists them into MongoDB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogConsumer {

    ObjectMapper objectMapper;
    AuditLogService auditLogService;

    @KafkaListener(
            topics = "${spring.kafka.topic.audit-log:${AUDIT_LOG_TOPIC:audit-topic}}",
            groupId = "${spring.kafka.consumer.group-id:${KAFKA_GROUP_ID:audit-service-group}}")
    public void onMessage(String payload) {
        if (payload == null || payload.isBlank()) {
            log.warn("Received empty audit payload - skipping.");
            return;
        }

        try {
            String normalized = unwrapJsonString(payload);
            AuditLogEvent event = objectMapper.readValue(normalized, AuditLogEvent.class);
            auditLogService.save(event);
        } catch (Exception ex) {
            log.error("Failed to process audit message: payload={} error={}", payload, ex.getMessage(), ex);
        }
    }

    /**
     * Decode only the outer JSON string layer, preserving escaped nested JSON
     * in fields like oldValue/newValue.
     */
    private String unwrapJsonString(String payload) throws JsonProcessingException {
        String trimmed = payload.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return objectMapper.readValue(trimmed, String.class);
        }
        return trimmed;
    }
}
