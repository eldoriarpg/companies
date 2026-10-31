ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS extended_hours     INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS extension_requests INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS extension_hours    INT          NULL,
    ADD COLUMN IF NOT EXISTS extension_reason   VARCHAR(256) NULL,
    ADD COLUMN IF NOT EXISTS extension_asked    DATETIME     NULL;
