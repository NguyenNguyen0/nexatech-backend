package com.nexatech.shipping.entity;

import com.nexatech.shipping.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_tracking_histories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentTrackingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column(length = 500)
    private String locationAddress;

    private Double lat;
    private Double lng;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime eventTime;

    @PrePersist
    void prePersist() {
        if (eventTime == null) {
            eventTime = LocalDateTime.now();
        }
    }
}
