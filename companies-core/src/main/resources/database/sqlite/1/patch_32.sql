CREATE TABLE jobs
(
    id          INTEGER  NOT NULL
        CONSTRAINT jobs_pk
            PRIMARY KEY AUTOINCREMENT,
    owner_uuid  blob     NOT NULL,
    kind        TEXT     NOT NULL,
    title       TEXT     NOT NULL,
    description TEXT,
    price       REAL     NOT NULL,
    fee         REAL     NOT NULL,
    state       TEXT     NOT NULL,
    company_id  INTEGER,
    node        blob     NOT NULL,
    world       TEXT     NOT NULL,
    min_x       INTEGER  NOT NULL,
    min_y       INTEGER  NOT NULL,
    min_z       INTEGER  NOT NULL,
    max_x       INTEGER  NOT NULL,
    max_y       INTEGER  NOT NULL,
    max_z       INTEGER  NOT NULL,
    land        TEXT     NOT NULL,
    land_ref    TEXT,
    due_hours   INTEGER  NOT NULL,
    created     datetime NOT NULL,
    expires     datetime NOT NULL,
    taken       datetime,
    due         datetime,
    closed      datetime,
    closed_by   TEXT,
    granted     INTEGER  NOT NULL DEFAULT 0
);

CREATE INDEX jobs_state_index
    ON jobs (state, expires);

CREATE INDEX jobs_company_index
    ON jobs (company_id, state);

CREATE INDEX jobs_node_index
    ON jobs (node, granted);

CREATE TABLE job_work
(
    job_id      INTEGER NOT NULL
        CONSTRAINT job_work_jobs_id_fk
            REFERENCES jobs
            ON DELETE CASCADE,
    worker_uuid blob    NOT NULL,
    blocks      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT job_work_pk
        PRIMARY KEY (job_id, worker_uuid)
);

CREATE TABLE job_grants
(
    job_id      INTEGER NOT NULL
        CONSTRAINT job_grants_jobs_id_fk
            REFERENCES jobs
            ON DELETE CASCADE,
    player_uuid blob    NOT NULL,
    CONSTRAINT job_grants_pk
        PRIMARY KEY (job_id, player_uuid)
);
