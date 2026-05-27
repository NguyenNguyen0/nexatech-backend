package com.nexatech.notification.event.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Envelope published to the central audit topic. Contract is shared across services;
 * the audit-service consumer deserializes this exact JSON shape.
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
    String entityLabel;
    String oldValue;
    String newValue;
    String actor;
    String actorId;
    String reason;
    String sourceService;
    String message;
    Map<String, Object> metadata;
    Long timestamp;
}
