ALTER TABLE companies_schema.order_states
    ADD COLUMN IF NOT EXISTS extensions INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE companies_schema.order_states
    ADD COLUMN IF NOT EXISTS delivery_bonus INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE companies_schema.order_states
    ADD COLUMN IF NOT EXISTS purge_warning TIMESTAMP;

ALTER TABLE companies_schema.item_returns
    ADD COLUMN IF NOT EXISTS kind INTEGER DEFAULT 0 NOT NULL;
