CREATE TABLE job_marks
(
    job_id     INTEGER NOT NULL
        CONSTRAINT job_marks_jobs_id_fk
            REFERENCES jobs
            ON DELETE CASCADE,
    company_id INTEGER NOT NULL,
    kind       INTEGER NOT NULL,
    pos        INTEGER NOT NULL,
    CONSTRAINT job_marks_pk
        PRIMARY KEY (job_id, kind, pos)
);

ALTER TABLE jobs
    ADD COLUMN air_at_post INTEGER;
