package com.nexatech.order.repository;

import com.nexatech.order.model.EntitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitySnapshotRepository extends JpaRepository<EntitySnapshot, UUID> {

    /**
     * Lấy snapshot gần nhất TRƯỚC thời điểm T — dùng làm base state cho point-in-time query.
     * Ví dụ: getStateAt(orderId, 14:30) → snapshot 14:00, rồi apply audit logs 14:00→14:30.
     */
    Optional<EntitySnapshot> findTopByEntityTypeAndEntityIdAndSnapshottedAtBeforeOrderBySnapshottedAtDesc(
            String entityType, UUID entityId, Instant before);

    /** Snapshot gần nhất của entity — dùng để kiểm tra có nên tạo snapshot mới không. */
    Optional<EntitySnapshot> findTopByEntityTypeAndEntityIdOrderBySnapshottedAtDesc(
            String entityType, UUID entityId);
}
