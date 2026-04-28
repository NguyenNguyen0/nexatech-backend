ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_method  VARCHAR(100)             DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS payment_method   VARCHAR(30),
    ADD COLUMN IF NOT EXISTS tracking_number  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS carrier_name     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS estimated_delivery TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS shipped_at       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS delivered_at     TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at);
CREATE INDEX IF NOT EXISTS idx_orders_payment_method ON orders(payment_method);
