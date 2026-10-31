ALTER TABLE jobs
    ADD COLUMN extended_hours INTEGER NOT NULL DEFAULT 0;

ALTER TABLE jobs
    ADD COLUMN extension_requests INTEGER NOT NULL DEFAULT 0;

ALTER TABLE jobs
    ADD COLUMN extension_hours INTEGER;

ALTER TABLE jobs
    ADD COLUMN extension_reason TEXT;

ALTER TABLE jobs
    ADD COLUMN extension_asked datetime;
