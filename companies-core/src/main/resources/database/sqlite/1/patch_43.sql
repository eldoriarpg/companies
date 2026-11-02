ALTER TABLE jobs
    ADD COLUMN cancel_asked datetime;

CREATE TABLE job_reviews
(
    id      INTEGER  NOT NULL
        CONSTRAINT job_reviews_pk
            PRIMARY KEY AUTOINCREMENT,
    job_id  INTEGER  NOT NULL
        CONSTRAINT job_reviews_jobs_id_fk
            REFERENCES jobs
            ON DELETE CASCADE,
    kind    INTEGER  NOT NULL,
    by_uuid blob     NOT NULL,
    reason  TEXT,
    reviewed_at datetime NOT NULL
);

CREATE INDEX job_reviews_job_index
    ON job_reviews (job_id, reviewed_at);
