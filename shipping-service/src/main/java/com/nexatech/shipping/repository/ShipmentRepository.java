package com.nexatech.shipping.repository;

import com.nexatech.shipping.entity.Shipment;
import com.nexatech.shipping.enums.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByTrackingCode(String trackingCode);

    Optional<Shipment> findByOrderId(String orderId);

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);
}
