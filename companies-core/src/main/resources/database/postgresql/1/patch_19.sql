CREATE TABLE IF NOT EXISTS companies_schema.setup_progress
(
    page       TEXT      NOT NULL,
    state      TEXT      NOT NULL,
    actor      TEXT      NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT setup_progress_pk
        PRIMARY KEY (page)
);
