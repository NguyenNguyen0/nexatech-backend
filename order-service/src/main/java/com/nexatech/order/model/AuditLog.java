package com.nexatech.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
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

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
