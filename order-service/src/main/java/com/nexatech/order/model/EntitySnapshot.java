package com.nexatech.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot state của một entity tại một thời điểm cụ thể.
 *
 * Mục đích: tối ưu point-in-time query. Thay vì replay toàn bộ audit_logs
 * từ đầu, chỉ cần lấy snapshot gần nhất rồi apply các audit log sau đó (delta).
 *
 * Ví dụ:
 *   snapshot lúc 14:00 → {status: PENDING, total: 500k}
 *   audit log 14:15   → {status: CONFIRMED}
 *   audit log 14:30   → {status: SHIPPED}
 *   → state lúc 14:20 = merge(snapshot, audit_14:15) = {status: CONFIRMED, total: 500k}
 */
@Entity
@Table(name = "entity_snapshots",
        indexes = @Index(name = "idx_snapshot_lookup",
                columnList = "entity_type, entity_id, snapshotted_at DESC"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /** Full state của entity — JSONB, queryable. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> state;

    @Column(name = "snapshotted_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant snapshottedAt = Instant.now();
}
