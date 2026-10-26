ALTER TABLE companies_schema.orders
    ADD COLUMN IF NOT EXISTS system_rule TEXT;
