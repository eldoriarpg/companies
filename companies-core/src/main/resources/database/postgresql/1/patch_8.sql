ALTER TABLE companies_schema.node
    ADD COLUMN IF NOT EXISTS last_seen TIMESTAMP;
