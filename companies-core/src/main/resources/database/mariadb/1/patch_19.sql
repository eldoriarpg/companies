CREATE TABLE IF NOT EXISTS setup_progress
(
    page       VARCHAR(32) NOT NULL,
    state      VARCHAR(16) NOT NULL,
    actor      VARCHAR(64) NOT NULL,
    changed_at DATETIME    NOT NULL,
    CONSTRAINT setup_progress_pk
        PRIMARY KEY (page)
);
