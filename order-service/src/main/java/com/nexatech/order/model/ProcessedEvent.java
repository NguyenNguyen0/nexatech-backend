package com.nexatech.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka consumer deduplication.
 * Mỗi khi consumer xử lý thành công một event, lưu eventId + consumerName vào bảng này.
 * Lần tiếp theo nếu Kafka re-deliver cùng message, consumer bỏ qua.
 */
@Entity
@Table(name = "processed_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_processed_event_consumer",
                columnNames = {"event_id", "consumer_name"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String consumerName;

    private String aggregateId;

    @CreationTimestamp
    private Instant processedAt;
}
