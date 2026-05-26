package com.nexatech.audit.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nexatech.audit.dto.event.AuditLogEvent;
import com.nexatech.audit.dto.response.AuditLogResponse;

public interface AuditLogService {

    AuditLogResponse save(AuditLogEvent event);

    AuditLogResponse getById(String id);

    Page<AuditLogResponse> search(
            String action,
            String entityType,
            String entityId,
            String actor,
            String sourceService,
            Instant from,
            Instant to,
            Pageable pageable);
}
