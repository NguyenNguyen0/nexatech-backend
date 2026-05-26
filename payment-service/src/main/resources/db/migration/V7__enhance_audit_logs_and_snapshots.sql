-- Nâng cấp audit_logs: thêm payload JSONB và correlation_id để hỗ trợ
-- structured audit và cross-service trace (lightweight event sourcing).

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS payload        JSONB,
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);

-- Index mới: tra cứu nhanh theo correlation (trace xuyên service)
CREATE INDEX IF NOT EXISTS idx_audit_correlation
    ON audit_logs (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Index hỗ trợ point-in-time query: entity + thời gian
CREATE INDEX IF NOT EXISTS idx_audit_entity_time
    ON audit_logs (entity_id, created_at);

-- Bảng snapshot: lưu toàn bộ state tại một thời điểm để tăng tốc point-in-time query.
-- Thay vì replay toàn bộ audit log từ đầu, lấy snapshot gần nhất rồi chỉ apply delta.
CREATE TABLE IF NOT EXISTS entity_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(100)             NOT NULL,
    entity_id       UUID                     NOT NULL,
    state           JSONB                    NOT NULL,
    snapshotted_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_snapshot_lookup
    ON entity_snapshots (entity_type, entity_id, snapshotted_at DESC);
