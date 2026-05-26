package com.nexatech.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit trail — chỉ INSERT, không UPDATE/DELETE.
 * Ghi lại mọi thay đổi quan trọng trong payment-service.
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 255)
    private String actor;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_service", length = 100)
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
     */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
