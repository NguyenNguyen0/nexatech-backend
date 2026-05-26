package com.nexatech.audit.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.audit.dto.event.AuditLogEvent;
import com.nexatech.audit.service.AuditLogService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens to audit envelopes from Kafka and persists them into MongoDB.
 * The topic and consumer group are declared in the config-server's {@code application.yml}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuditLogConsumer {

    ObjectMapper objectMapper;
    AuditLogService auditLogService;

    @KafkaListener(
            topics = "${spring.kafka.topic.audit-log}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(String payload) {
        if (payload == null || payload.isBlank()) {
            log.warn("Received empty audit payload — skipping.");
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
     * Some producers publish a JSON string wrapped in extra double quotes (double-encoded).
     * This method strips that escape layer so we get a valid JSON object before deserialization.
     */
    private String unwrapJsonString(String payload) {
        String trimmed = payload.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).replace("\\\"", "\"");
        }
        return trimmed;
    }
}
