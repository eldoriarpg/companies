ALTER TABLE companies_schema.node
    ADD COLUMN IF NOT EXISTS restart_pending TEXT;
