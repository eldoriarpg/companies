CREATE TABLE IF NOT EXISTS job_marks
(
    job_id     INT      NOT NULL,
    company_id INT      NOT NULL,
    kind       SMALLINT NOT NULL,
    pos        BIGINT   NOT NULL,
    CONSTRAINT job_marks_pk
        PRIMARY KEY (job_id, kind, pos),
    CONSTRAINT job_marks_jobs_id_fk
        FOREIGN KEY (job_id) REFERENCES jobs (id)
            ON DELETE CASCADE
);

ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS air_at_post BIGINT;
