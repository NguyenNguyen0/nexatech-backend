package com.nexatech.shipping.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_shipping_processed_event_consumer", columnNames = {"event_id", "consumer_name"})
})
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

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "aggregate_id", length = 100)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant processedAt = Instant.now();
}
