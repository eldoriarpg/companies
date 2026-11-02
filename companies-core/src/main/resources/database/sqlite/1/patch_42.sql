ALTER TABLE jobs
    ADD COLUMN paid_cents INTEGER NOT NULL DEFAULT 0;

UPDATE jobs
SET paid_cents = ROUND(paid * 100)
WHERE paid <> 0;

ALTER TABLE jobs
    DROP COLUMN paid;
