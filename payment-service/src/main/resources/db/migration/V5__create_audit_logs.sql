-- Audit Log cho Payment Service
CREATE TABLE IF NOT EXISTS audit_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    action         VARCHAR(100)             NOT NULL,
    entity_type    VARCHAR(100)             NOT NULL,
    entity_id      VARCHAR(100)             NOT NULL,
    old_value      TEXT,
    new_value      TEXT,
    actor          VARCHAR(255),
    reason         TEXT,
    source_service VARCHAR(100),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity      ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_created_at  ON audit_logs (created_at);
