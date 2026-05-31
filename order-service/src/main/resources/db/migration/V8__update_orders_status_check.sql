ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_status_check;

ALTER TABLE orders
    ADD CONSTRAINT orders_status_check
    CHECK (status IN (
        'PENDING',
        'CONFIRMED',
        'PROCESSING',
        'SHIPPED',
        'DELIVERED',
        'COMPLETED',
        'CANCELLED',
        'RETURNED',
        'OUT_OF_STOCK',
        'SHIPPING_FAILED',
        'REFUND_PENDING',
        'REFUNDED'
    ));
