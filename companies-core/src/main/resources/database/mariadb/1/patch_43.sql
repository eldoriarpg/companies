ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS cancel_asked DATETIME NULL;

CREATE TABLE IF NOT EXISTS job_reviews
(
    id      INT          NOT NULL AUTO_INCREMENT,
    job_id  INT          NOT NULL,
    kind    SMALLINT     NOT NULL,
    by_uuid BINARY(16)   NOT NULL,
    reason  VARCHAR(256) NULL,
    reviewed_at DATETIME     NOT NULL,
    CONSTRAINT job_reviews_pk
        PRIMARY KEY (id),
    CONSTRAINT job_reviews_jobs_id_fk
        FOREIGN KEY (job_id) REFERENCES jobs (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS job_reviews_job_index
    ON job_reviews (job_id, reviewed_at);
