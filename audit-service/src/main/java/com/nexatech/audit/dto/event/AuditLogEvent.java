package com.nexatech.audit.dto.event;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Standard envelope for audit events published to the Kafka topic {@code audit-topic}.
 * All business services must follow this structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogEvent {

    String action;
    String entityType;
    String entityId;
    String oldValue;
    String newValue;
    String actor;
    String reason;
    String sourceService;
    String message;
    Map<String, Object> metadata;

    /** Epoch millis — when the event occurred on the producer side. */
    Long timestamp;
}
