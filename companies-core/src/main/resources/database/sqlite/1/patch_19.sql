CREATE TABLE setup_progress
(
    page       TEXT     NOT NULL,
    state      TEXT     NOT NULL,
    actor      TEXT     NOT NULL,
    changed_at datetime NOT NULL,
    CONSTRAINT setup_progress_pk
        PRIMARY KEY (page)
);
