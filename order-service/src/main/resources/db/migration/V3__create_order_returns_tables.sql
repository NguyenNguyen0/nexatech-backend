CREATE TABLE order_returns (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID        NOT NULL,
    customer_id UUID        NOT NULL,
    reason      TEXT        NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    admin_note  TEXT,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_return_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_return_id UUID         NOT NULL REFERENCES order_returns(id) ON DELETE CASCADE,
    product_id      VARCHAR(100) NOT NULL,
    product_name    VARCHAR(255),
    quantity        INTEGER      NOT NULL DEFAULT 1,
    item_reason     TEXT,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_order_returns_order_id    ON order_returns(order_id);
CREATE INDEX idx_order_returns_customer_id ON order_returns(customer_id);
CREATE INDEX idx_order_returns_status      ON order_returns(status);
CREATE INDEX idx_order_return_items_return_id ON order_return_items(order_return_id);
