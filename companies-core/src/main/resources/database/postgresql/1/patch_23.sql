ALTER TABLE companies_schema.system_order_decisions
    ADD COLUMN IF NOT EXISTS outcome TEXT;

ALTER TABLE companies_schema.system_order_decisions
    ADD COLUMN IF NOT EXISTS ended_at TIMESTAMP;
