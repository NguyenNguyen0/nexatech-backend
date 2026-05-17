-- Idempotency / Deduplication cho Kafka consumer
CREATE TABLE IF NOT EXISTS processed_events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      UUID         NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    aggregate_id  VARCHAR(100),
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_processed_event_consumer UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_processed_event_id ON processed_events (event_id);
