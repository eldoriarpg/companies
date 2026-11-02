CREATE TABLE IF NOT EXISTS companies_schema.job_marks
(
    job_id     INTEGER  NOT NULL
        CONSTRAINT job_marks_jobs_id_fk
            REFERENCES companies_schema.jobs
            ON DELETE CASCADE,
    company_id INTEGER  NOT NULL,
    kind       SMALLINT NOT NULL,
    pos        BIGINT   NOT NULL,
    CONSTRAINT job_marks_pk
        PRIMARY KEY (job_id, kind, pos)
);

ALTER TABLE companies_schema.jobs
    ADD COLUMN IF NOT EXISTS air_at_post BIGINT;
