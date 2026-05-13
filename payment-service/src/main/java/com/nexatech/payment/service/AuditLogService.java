package com.nexatech.payment.service;

import com.nexatech.payment.model.AuditLog;
import com.nexatech.payment.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi audit log bất đồng bộ — không chặn transaction nghiệp vụ.
 * REQUIRES_NEW: audit log được commit riêng, không bị rollback theo transaction chính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SOURCE_SERVICE = "payment-service";

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
            log.error("Không thể ghi audit log: action={} entity={}:{} error={}", action, entityType, entityId, e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId, String newValue, String actor) {
        log(action, entityType, entityId, null, newValue, actor, null);
    }
}
