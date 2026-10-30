CREATE TABLE IF NOT EXISTS companies_schema.jobs
(
    id          SERIAL           NOT NULL,
    owner_uuid  BYTEA            NOT NULL,
    kind        TEXT             NOT NULL,
    title       TEXT             NOT NULL,
    description TEXT,
    price       DOUBLE PRECISION NOT NULL,
    fee         DOUBLE PRECISION NOT NULL,
    state       TEXT             NOT NULL,
    company_id  INTEGER,
    node        BYTEA            NOT NULL,
    world       TEXT             NOT NULL,
    min_x       INTEGER          NOT NULL,
    min_y       INTEGER          NOT NULL,
    min_z       INTEGER          NOT NULL,
    max_x       INTEGER          NOT NULL,
    max_y       INTEGER          NOT NULL,
    max_z       INTEGER          NOT NULL,
    land        TEXT             NOT NULL,
    land_ref    TEXT,
    due_hours   INTEGER          NOT NULL,
    created     TIMESTAMP        NOT NULL,
    expires     TIMESTAMP        NOT NULL,
    taken       TIMESTAMP,
    due         TIMESTAMP,
    closed      TIMESTAMP,
    closed_by   TEXT,
    granted     BOOLEAN          NOT NULL DEFAULT FALSE,
    done_at     TIMESTAMP,
    done_by     BYTEA,
    rejected    VARCHAR(256),
    dispute_by  BYTEA,
    dispute     VARCHAR(256),
    disputed_at TIMESTAMP,
    settled     INTEGER,
    target_y    INTEGER,
    target_block VARCHAR(64),
    CONSTRAINT jobs_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS jobs_state_index
    ON companies_schema.jobs (state, expires);

CREATE INDEX IF NOT EXISTS jobs_company_index
    ON companies_schema.jobs (company_id, state);

CREATE INDEX IF NOT EXISTS jobs_node_index
    ON companies_schema.jobs (node, granted);

CREATE TABLE IF NOT EXISTS companies_schema.job_work
(
    job_id      INTEGER NOT NULL
        CONSTRAINT job_work_jobs_id_fk
            REFERENCES companies_schema.jobs
            ON DELETE CASCADE,
    worker_uuid BYTEA   NOT NULL,
    blocks      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT job_work_pk
        PRIMARY KEY (job_id, worker_uuid)
);

CREATE TABLE IF NOT EXISTS companies_schema.job_grants
(
    job_id      INTEGER NOT NULL
        CONSTRAINT job_grants_jobs_id_fk
            REFERENCES companies_schema.jobs
            ON DELETE CASCADE,
    player_uuid BYTEA   NOT NULL,
    CONSTRAINT job_grants_pk
        PRIMARY KEY (job_id, player_uuid)
);
