ALTER TABLE companies_schema.jobs
    ADD COLUMN IF NOT EXISTS extended_hours     INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS extension_requests INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS extension_hours    INTEGER,
    ADD COLUMN IF NOT EXISTS extension_reason   VARCHAR(256),
    ADD COLUMN IF NOT EXISTS extension_asked    TIMESTAMP;
