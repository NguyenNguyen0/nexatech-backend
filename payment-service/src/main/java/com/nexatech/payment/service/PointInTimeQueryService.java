package com.nexatech.payment.service;

import com.nexatech.payment.model.AuditLog;
import com.nexatech.payment.model.EntitySnapshot;
import com.nexatech.payment.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hỗ trợ hai loại query lịch sử:
 *
 * 1. Point-in-time query: "Payment X trông như thế nào lúc 10:06?"
 *    Thuật toán:
 *      a. Tìm snapshot gần nhất TRƯỚC thời điểm T
 *      b. Apply các audit_log SAU snapshot đó đến thời điểm T (merge payload)
 *      c. Trả về state reconstruct
 *
 * 2. Cross-service timeline: "Toàn bộ luồng thanh toán từ đầu đến cuối?"
 *    → Query theo correlationId (OpenTelemetry traceId)
 *    → Thấy PAYMENT_INITIATED → PAYMENT_COMPLETED ngay cả khi trigger từ order-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointInTimeQueryService {

    private final AuditLogRepository auditLogRepository;
    private final SnapshotService snapshotService;

    /**
     * Reconstruct state của entity tại thời điểm T.
     *
     * @param entityType "Payment"
     * @param entityId   UUID của entity
     * @param pointInTime Thời điểm muốn xem state
     * @return Map chứa state của entity tại thời điểm T (merge của snapshot + delta audit logs)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStateAt(String entityType, UUID entityId, Instant pointInTime) {
        // Bước 1: Tìm snapshot gần nhất trước T
        EntitySnapshot snapshot = snapshotService
                .findLatestBefore(entityType, entityId, pointInTime)
                .orElse(null);

        Instant deltaFrom = snapshot != null ? snapshot.getSnapshottedAt() : Instant.EPOCH;
        Map<String, Object> state = snapshot != null
                ? new HashMap<>(snapshot.getState())
                : new HashMap<>();

        // Bước 2: Apply audit logs sau snapshot đến thời điểm T
        List<AuditLog> deltas = auditLogRepository
                .findByEntityIdAndCreatedAtAfterAndCreatedAtBeforeOrderByCreatedAtAsc(
                        entityId.toString(), deltaFrom, pointInTime);

        for (AuditLog delta : deltas) {
            if (delta.getPayload() != null) {
                state.putAll(delta.getPayload());
            }
        }

        log.debug("Reconstructed state of {} {} at {}: {} audit logs applied (snapshot={})",
                entityType, entityId, pointInTime, deltas.size(), snapshot != null ? snapshot.getSnapshottedAt() : "none");
        return state;
    }

    /**
     * Lấy toàn bộ lịch sử của entity theo thứ tự thời gian.
     * Mỗi entry = 1 sự kiện với payload đầy đủ.
     *
     * @param entityId UUID của entity (Payment)
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getTimeline(UUID entityId) {
        return auditLogRepository.findByEntityIdOrderByCreatedAtAsc(entityId.toString());
    }

    /**
     * Trace toàn bộ luồng nghiệp vụ xuyên service theo correlationId (traceId).
     *
     * Ví dụ kết quả:
     *   [payment-service] PAYMENT_INITIATED        10:00 — {paymentId, orderId, amount, method}
     *   [payment-service] ORDER_CONFIRMED_RECEIVED  10:02 — {orderId, triggeredBy: "kafka"}
     *   [payment-service] PAYMENT_COMPLETED         10:05 — {paymentId, txnId, completedAt}
     *
     * @param correlationId OpenTelemetry traceId (propagate qua HTTP header "traceparent")
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getTimelineByCorrelation(String correlationId) {
        return auditLogRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId);
    }
}
