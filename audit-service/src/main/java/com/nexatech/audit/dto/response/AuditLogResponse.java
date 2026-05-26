package com.nexatech.audit.dto.response;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogResponse {

    String id;
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
    Instant timestamp;
    Instant createdAt;
}
