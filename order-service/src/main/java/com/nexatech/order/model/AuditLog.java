package com.nexatech.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log cho Order Service.
 * Ghi lại các hành động quan trọng để tracking, debug và phục vụ báo cáo.
 * Không cần update — chỉ insert (immutable log).
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_entity",     columnList = "entityType, entityId"),
                @Index(name = "idx_audit_created_at", columnList = "createdAt")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Hành động thực hiện, ví dụ: ORDER_CREATED, ORDER_STATUS_CHANGED, ORDER_CANCELLED */
    @Column(nullable = false)
    private String action;

    /** Loại entity, ví dụ: "Order", "OrderReturn" */
    @Column(nullable = false)
    private String entityType;

    /** ID của entity bị tác động */
    @Column(nullable = false)
    private String entityId;

    /** Giá trị cũ (JSON hoặc text) — null nếu là thao tác tạo mới */
    @Column(columnDefinition = "TEXT")
    private String oldValue;

    /** Giá trị mới (JSON hoặc text) */
    @Column(columnDefinition = "TEXT")
    private String newValue;

    /** Actor thực hiện: userId (UUID string) hoặc "system" / "kafka-consumer" */
    private String actor;

    /** Lý do hoặc ngữ cảnh thêm */
    @Column(columnDefinition = "TEXT")
    private String reason;

    /** Service nào tạo log này */
    private String sourceService;

    /**
     * Payload JSONB: toàn bộ state của entity tại thời điểm event xảy ra.
     * Dùng để point-in-time reconstruction — replay từng payload theo createdAt.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /**
     * Correlation ID (OpenTelemetry traceId) — liên kết audit log
     * xuyên suốt các service trong cùng một luồng nghiệp vụ.
     * Ví dụ: ORDER_CREATED → PAYMENT_INITIATED → PAYMENT_COMPLETED đều cùng correlationId.
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
