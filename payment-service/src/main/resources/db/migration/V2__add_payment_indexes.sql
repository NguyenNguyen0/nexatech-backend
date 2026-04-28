ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_transaction_id ON payments(transaction_id)
    WHERE transaction_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_method      ON payments(method);
CREATE INDEX IF NOT EXISTS idx_payments_created_at  ON payments(created_at);
