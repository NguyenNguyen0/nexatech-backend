package com.nexatech.order.service;

import com.nexatech.order.model.EntitySnapshot;
import com.nexatech.order.repository.EntitySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Quản lý snapshot state của entity để tối ưu point-in-time query.
 *
 * Khi nào nên gọi takeSnapshot():
 *   - Sau các state transition quan trọng: DELIVERED, COMPLETED, RETURNED
 *   - Tránh gọi mỗi event (quá nhiều snapshot không cần thiết)
 *
 * Snapshot + AuditLog = lightweight Event Sourcing:
 *   state(T) = nearest_snapshot_before(T) + apply(audit_logs_after_snapshot_until_T)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final EntitySnapshotRepository snapshotRepository;

    /**
     * Lưu snapshot state hiện tại của entity.
     * @Async để không block luồng nghiệp vụ chính.
     * REQUIRES_NEW để snapshot được commit độc lập.
     *
     * @param entityType  "Order", "OrderReturn"
     * @param entityId    UUID của entity
     * @param currentState Full state hiện tại dưới dạng Map (sẽ lưu thành JSONB)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void takeSnapshot(String entityType, UUID entityId, Map<String, Object> currentState) {
        try {
            snapshotRepository.save(EntitySnapshot.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .state(currentState)
                    .build());
            log.debug("Đã lưu snapshot: type={} id={}", entityType, entityId);
        } catch (Exception e) {
            log.warn("Không thể lưu snapshot: type={} id={}: {}", entityType, entityId, e.getMessage());
        }
    }

    /**
     * Lấy snapshot gần nhất trước thời điểm T (dùng bởi PointInTimeQueryService).
     */
    public Optional<EntitySnapshot> findLatestBefore(String entityType, UUID entityId, Instant before) {
        return snapshotRepository.findTopByEntityTypeAndEntityIdAndSnapshottedAtBeforeOrderBySnapshottedAtDesc(
                entityType, entityId, before);
    }
}
