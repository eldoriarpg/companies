ALTER TABLE companies_schema.jobs
    ADD COLUMN IF NOT EXISTS dug_to_stock BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies_schema.jobs
    ADD COLUMN IF NOT EXISTS fill_block VARCHAR(64) NULL;
