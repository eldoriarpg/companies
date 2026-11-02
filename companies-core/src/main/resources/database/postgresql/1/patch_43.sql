ALTER TABLE companies_schema.jobs
    ADD COLUMN IF NOT EXISTS cancel_asked TIMESTAMP;

CREATE TABLE IF NOT EXISTS companies_schema.job_reviews
(
    id        SERIAL       NOT NULL,
    job_id    INTEGER      NOT NULL
        CONSTRAINT job_reviews_jobs_id_fk
            REFERENCES companies_schema.jobs
            ON DELETE CASCADE,
    kind      SMALLINT     NOT NULL,
    by_uuid   BYTEA        NOT NULL,
    reason    VARCHAR(256),
    reviewed_at TIMESTAMP    NOT NULL,
    CONSTRAINT job_reviews_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS job_reviews_job_index
    ON companies_schema.job_reviews (job_id, reviewed_at);
