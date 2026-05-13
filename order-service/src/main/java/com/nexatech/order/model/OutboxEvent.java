package com.nexatech.order.model;

import com.nexatech.order.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox Pattern entity.
 * Event được lưu cùng transaction nghiệp vụ → scheduler đọc và publish lên Kafka.
 * Đảm bảo at-least-once delivery ngay cả khi Kafka tạm thời unavailable.
 */
@Entity
@Table(name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, createdAt"),
                @Index(name = "idx_outbox_aggregate",      columnList = "aggregateType, aggregateId")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Loại aggregate, ví dụ: "Order", "OrderReturn" */
    @Column(nullable = false)
    private String aggregateType;

    /** ID của aggregate (orderId, returnId...), dùng làm Kafka message key */
    @Column(nullable = false)
    private String aggregateId;

    /** Tên event, ví dụ: "ORDER_PLACED", "ORDER_STATUS_UPDATED" */
    @Column(nullable = false)
    private String eventType;

    /** JSON payload đã serialize — được gửi nguyên vẹn lên Kafka */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant publishedAt;
}
