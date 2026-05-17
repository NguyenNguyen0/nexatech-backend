package com.nexatech.shipping.service;

import com.nexatech.shipping.entity.AuditLog;
import com.nexatech.shipping.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SOURCE_SERVICE = "shipping-service";

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId,
                    String oldValue, String newValue, String actor, String reason) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .actor(actor)
                    .reason(reason)
                    .sourceService(SOURCE_SERVICE)
                    .build());
        } catch (Exception e) {
            log.error("Unable to write audit log: action={} entity={}:{} error={}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId, String newValue, String actor) {
        log(action, entityType, entityId, null, newValue, actor, null);
    }
}
