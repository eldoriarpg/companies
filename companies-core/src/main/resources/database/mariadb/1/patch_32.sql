CREATE TABLE IF NOT EXISTS jobs
(
    id          INT AUTO_INCREMENT NOT NULL,
    owner_uuid  BINARY(16)         NOT NULL,
    kind        VARCHAR(16)        NOT NULL,
    title       VARCHAR(64)        NOT NULL,
    description TEXT               NULL,
    price       DOUBLE             NOT NULL,
    fee         DOUBLE             NOT NULL,
    state       VARCHAR(16)        NOT NULL,
    company_id  INT                NULL,
    node        BINARY(16)         NOT NULL,
    world       VARCHAR(64)        NOT NULL,
    min_x       INT                NOT NULL,
    min_y       INT                NOT NULL,
    min_z       INT                NOT NULL,
    max_x       INT                NOT NULL,
    max_y       INT                NOT NULL,
    max_z       INT                NOT NULL,
    land        VARCHAR(16)        NOT NULL,
    land_ref    VARCHAR(128)       NULL,
    due_hours   INT                NOT NULL,
    created     DATETIME           NOT NULL,
    expires     DATETIME           NOT NULL,
    taken       DATETIME           NULL,
    due         DATETIME           NULL,
    closed      DATETIME           NULL,
    closed_by   VARCHAR(64)        NULL,
    granted     BOOLEAN            NOT NULL DEFAULT FALSE,
    done_at     DATETIME           NULL,
    done_by     BINARY(16)         NULL,
    rejected    VARCHAR(256)       NULL,
    dispute_by  BINARY(16)         NULL,
    dispute     VARCHAR(256)       NULL,
    disputed_at DATETIME           NULL,
    settled     INT                NULL,
    CONSTRAINT jobs_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS jobs_state_index
    ON jobs (state, expires);

CREATE INDEX IF NOT EXISTS jobs_company_index
    ON jobs (company_id, state);

CREATE INDEX IF NOT EXISTS jobs_node_index
    ON jobs (node, granted);

CREATE TABLE IF NOT EXISTS job_work
(
    job_id      INT        NOT NULL,
    worker_uuid BINARY(16) NOT NULL,
    blocks      INT        NOT NULL DEFAULT 0,
    CONSTRAINT job_work_pk
        PRIMARY KEY (job_id, worker_uuid),
    CONSTRAINT job_work_jobs_id_fk
        FOREIGN KEY (job_id) REFERENCES jobs (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS job_grants
(
    job_id      INT        NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    CONSTRAINT job_grants_pk
        PRIMARY KEY (job_id, player_uuid),
    CONSTRAINT job_grants_jobs_id_fk
        FOREIGN KEY (job_id) REFERENCES jobs (id)
            ON DELETE CASCADE
);
