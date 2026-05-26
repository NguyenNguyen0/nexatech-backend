package com.nexatech.payment.repository;

import com.nexatech.payment.model.EntitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EntitySnapshotRepository extends JpaRepository<EntitySnapshot, UUID> {

    /**
     * Lấy snapshot gần nhất TRƯỚC thời điểm T — dùng làm base state cho point-in-time query.
     * Ví dụ: getStateAt(paymentId, 10:30) → snapshot 10:00, rồi apply audit logs 10:00→10:30.
     */
    Optional<EntitySnapshot> findTopByEntityTypeAndEntityIdAndSnapshottedAtBeforeOrderBySnapshottedAtDesc(
            String entityType, UUID entityId, Instant before);

    /** Snapshot gần nhất của entity — dùng để kiểm tra có nên tạo snapshot mới không. */
    Optional<EntitySnapshot> findTopByEntityTypeAndEntityIdOrderBySnapshottedAtDesc(
            String entityType, UUID entityId);
}
