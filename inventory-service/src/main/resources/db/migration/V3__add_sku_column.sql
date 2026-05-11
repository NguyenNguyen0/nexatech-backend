-- Add sku column to inventory_items (safe for new and existing databases)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns 
    WHERE table_name = 'inventory_items' AND column_name = 'sku'
  ) THEN
    ALTER TABLE inventory_items ADD COLUMN sku VARCHAR(100) UNIQUE;
  END IF;
END $$;

-- Create index on sku if it doesn't exist (for faster lookups during CSV import)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.indexes 
    WHERE indexname = 'idx_inventory_sku'
  ) THEN
    CREATE INDEX idx_inventory_sku ON inventory_items(sku);
  END IF;
END $$;

-- Note: Backfill of sku values should be handled via application startup logic or manual data migration
-- if existing inventory items need to be mapped to products for CSV import.
