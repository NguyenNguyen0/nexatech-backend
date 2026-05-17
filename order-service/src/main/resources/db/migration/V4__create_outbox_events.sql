-- Outbox Pattern: lưu event vào DB cùng transaction nghiệp vụ
-- Scheduler sẽ đọc PENDING events và publish lên Kafka
CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100)             NOT NULL,
    aggregate_id    VARCHAR(100)             NOT NULL,
    event_type      VARCHAR(100)             NOT NULL,
    payload         TEXT                     NOT NULL,
    status          VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    retry_count     INT                      NOT NULL DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);
CREATE INDEX idx_outbox_aggregate      ON outbox_events (aggregate_type, aggregate_id);
