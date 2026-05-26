package com.nexatech.payment.service;

import com.nexatech.payment.model.AuditLog;
import com.nexatech.payment.repository.AuditLogRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Ghi audit log bất đồng bộ — không chặn transaction nghiệp vụ.
 * REQUIRES_NEW: audit log được commit riêng, không bị rollback theo transaction chính.
 *
 * logEvent() lưu payload JSONB (full state) và correlationId (traceId từ OpenTelemetry)
 * để hỗ trợ point-in-time query và cross-service trace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final String SOURCE_SERVICE = "payment-service";

    private final AuditLogRepository auditLogRepository;
    private final ObjectProvider<Tracer> tracerProvider;

    /**
     * Ghi audit log với structured payload (JSONB).
     * correlationId tự động lấy từ OpenTelemetry traceId — truyền xuyên service qua HTTP header.
     *
     * @param action     Tên sự kiện, ví dụ "PAYMENT_CREATED", "PAYMENT_COMPLETED"
     * @param entityType Loại entity: "Payment"
     * @param entityId   ID của entity (UUID dưới dạng String)
     * @param payload    Toàn bộ state của entity tại thời điểm sự kiện xảy ra
     * @param actor      Người thực hiện: userId hoặc "system" / "vnpay" / "kafka-consumer"
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String action, String entityType, String entityId,
                         Map<String, Object> payload, String actor) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .payload(payload)
                    .actor(actor)
                    .correlationId(resolveCorrelationId())
                    .sourceService(SOURCE_SERVICE)
                    .build());
        } catch (Exception e) {
            log.error("Không thể ghi audit log: action={} entity={}:{} error={}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    /** Lấy traceId từ Micrometer Tracer (OpenTelemetry) — đã tự động propagate qua HTTP header. */
    private String resolveCorrelationId() {
        try {
            Tracer tracer = tracerProvider.getIfAvailable();
            if (tracer == null) return null;
            Span span = tracer.currentSpan();
            return span != null ? span.context().traceId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Legacy methods (giữ backward compat) ====================

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
                    .correlationId(resolveCorrelationId())
                    .sourceService(SOURCE_SERVICE)
                    .build());
        } catch (Exception e) {
            log.error("Không thể ghi audit log: action={} entity={}:{} error={}",
                    action, entityType, entityId, e.getMessage());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, String entityId, String newValue, String actor) {
        log(action, entityType, entityId, null, newValue, actor, null);
    }
}
