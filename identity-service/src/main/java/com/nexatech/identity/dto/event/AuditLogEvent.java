package com.nexatech.identity.dto.event;

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
    /** Friendly label of the entity (e.g. username of the target User). */
    String entityLabel;
    String oldValue;
    String newValue;
    /** Display name of the performer (username / email / "system"). */
    String actor;
    /** Stable id of the performer (Keycloak sub / UUID). Optional. */
    String actorId;
    String reason;
    String sourceService;
    String message;
    Map<String, Object> metadata;

    /** Epoch millis — when the event occurred on the producer side. */
    Long timestamp;
}
