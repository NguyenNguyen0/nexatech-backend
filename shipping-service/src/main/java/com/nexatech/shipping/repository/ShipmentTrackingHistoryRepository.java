package com.nexatech.shipping.repository;

import com.nexatech.shipping.entity.ShipmentTrackingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentTrackingHistoryRepository extends JpaRepository<ShipmentTrackingHistory, UUID> {

    List<ShipmentTrackingHistory> findByShipmentIdOrderByEventTimeAsc(UUID shipmentId);
}
