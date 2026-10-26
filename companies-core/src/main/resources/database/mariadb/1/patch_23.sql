ALTER TABLE system_order_decisions
    ADD COLUMN IF NOT EXISTS outcome VARCHAR(16) NULL;

ALTER TABLE system_order_decisions
    ADD COLUMN IF NOT EXISTS ended_at DATETIME NULL;
