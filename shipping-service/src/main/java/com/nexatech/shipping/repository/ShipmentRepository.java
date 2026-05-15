package com.nexatech.shipping.repository;

import com.nexatech.shipping.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByTrackingCode(String trackingCode);

    Optional<Shipment> findByOrderId(String orderId);
}
