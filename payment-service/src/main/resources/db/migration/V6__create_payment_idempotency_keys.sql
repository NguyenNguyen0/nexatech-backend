-- Idempotency Key cho Payment API
-- Ngăn tạo giao dịch trùng khi client retry hoặc network timeout
CREATE TABLE IF NOT EXISTS payment_idempotency_keys (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key  VARCHAR(255)             NOT NULL UNIQUE,
    order_id         UUID                     NOT NULL,
    payment_id       UUID,
    request_hash     VARCHAR(64),
    response_payload TEXT,
    status           VARCHAR(20)              NOT NULL DEFAULT 'PROCESSING',
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_idempotency_key        ON payment_idempotency_keys (idempotency_key);
CREATE INDEX        idx_idempotency_order_id   ON payment_idempotency_keys (order_id);
