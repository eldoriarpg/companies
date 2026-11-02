ALTER TABLE companies_schema.jobs
    ADD COLUMN IF NOT EXISTS paid_cents BIGINT NOT NULL DEFAULT 0;

UPDATE companies_schema.jobs
SET paid_cents = ROUND(paid * 100)
WHERE paid <> 0;

ALTER TABLE companies_schema.jobs
    DROP COLUMN IF EXISTS paid;
