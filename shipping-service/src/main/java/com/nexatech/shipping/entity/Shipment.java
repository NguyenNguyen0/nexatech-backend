package com.nexatech.shipping.entity;

import com.nexatech.shipping.enums.ShipmentStatus;
import com.nexatech.shipping.enums.ShippingProvider;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShippingProvider provider;

    @Column(nullable = false, unique = true)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    @Column(precision = 19, scale = 2)
    private BigDecimal shippingFee;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    @Column(nullable = false, length = 500)
    private String customerAddress;

    private String customerProvince;
    private String customerDistrict;
    private String customerWard;
    private Integer toDistrictId;
    private String toWardCode;
    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer height;

    @Column(precision = 19, scale = 2)
    private BigDecimal codAmount;

    private Double currentLat;
    private Double currentLng;

    @Column(length = 500)
    private String currentAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
