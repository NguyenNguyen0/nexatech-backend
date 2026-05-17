package com.nexatech.order.service;

import com.nexatech.order.model.AuditLog;
import com.nexatech.order.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi audit log cho các hành động quan trọng trong Order Service.
 * Dùng REQUIRES_NEW để audit log được commit ngay cả khi transaction chính rollback.
 * @Async để không block luồng nghiệp vụ chính.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SERVICE_NAME = "order-service";

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId,
                    String oldValue, String newValue, String actor, String reason) {
        try {
            AuditLog log = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .actor(actor)
                    .reason(reason)
                    .sourceService(SERVICE_NAME)
                    .build();
            auditLogRepository.save(log);
        } catch (Exception e) {
            // Audit log không được làm crash nghiệp vụ chính
            log.warn("Không thể ghi audit log action={} entity={}/{}: {}", action, entityType, entityId, e.getMessage());
        }
    }

    /** Shortcut khi không cần oldValue / reason */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId, String newValue, String actor) {
        log(action, entityType, entityId, null, newValue, actor, null);
    }
}
