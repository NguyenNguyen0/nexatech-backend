package com.nexatech.order.service;

import com.nexatech.order.model.AuditLog;
import com.nexatech.order.model.EntitySnapshot;
import com.nexatech.order.repository.AuditLogRepository;
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
 * 1. Point-in-time query: "Order X trông như thế nào lúc 14:32 ngày 20/05?"
 *    Thuật toán:
 *      a. Tìm snapshot gần nhất TRƯỚC thời điểm T
 *      b. Apply các audit_log SAU snapshot đó đến thời điểm T (merge payload)
 *      c. Trả về state reconstruct
 *
 * 2. Cross-service timeline: "Toàn bộ luồng của order-123 từ đầu đến cuối?"
 *    → Query theo correlationId (OpenTelemetry traceId)
 *    → Thấy ORDER_PLACED → PAYMENT_INITIATED → PAYMENT_COMPLETED → ORDER_CONFIRMED
 *       ngay cả khi các event đến từ nhiều service khác nhau.
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
     * @param entityType "Order", "OrderReturn"
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
     * @param entityId UUID của entity (Order hoặc OrderReturn)
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getTimeline(UUID entityId) {
        return auditLogRepository.findByEntityIdOrderByCreatedAtAsc(entityId.toString());
    }

    /**
     * Trace toàn bộ luồng nghiệp vụ xuyên service theo correlationId (traceId).
     *
     * Ví dụ kết quả:
     *   [order-service]   ORDER_PLACED       14:00 — {orderId, customerId, total}
     *   [payment-service] PAYMENT_INITIATED  14:00 — {paymentId, orderId, amount}
     *   [payment-service] PAYMENT_COMPLETED  14:05 — {paymentId, txnId}
     *   [order-service]   ORDER_CONFIRMED    14:05 — {orderId, status}
     *
     * Lưu ý: kết quả chỉ chứa log từ order-service. Log từ payment-service
     * query riêng tại payment-service/PointInTimeQueryService.getTimelineByCorrelation().
     *
     * @param correlationId OpenTelemetry traceId (propagate qua HTTP header "traceparent")
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getTimelineByCorrelation(String correlationId) {
        return auditLogRepository.findByCorrelationIdOrderByCreatedAtAsc(correlationId);
    }
}
