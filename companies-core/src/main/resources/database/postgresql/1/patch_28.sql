CREATE TABLE IF NOT EXISTS companies_schema.server_events
(
    id              SERIAL           NOT NULL,
    name            TEXT             NOT NULL,
    kind            TEXT             NOT NULL,
    value           DOUBLE PRECISION NOT NULL,
    scope_materials TEXT,
    scope_levels    TEXT,
    starts          TIMESTAMP        NOT NULL,
    ends            TIMESTAMP        NOT NULL,
    funding         TEXT,
    account         TEXT,
    budget          DOUBLE PRECISION,
    spent           DOUBLE PRECISION NOT NULL DEFAULT 0,
    state           TEXT             NOT NULL,
    template        TEXT,
    created_by      TEXT             NOT NULL,
    created         TIMESTAMP        NOT NULL,
    closed          TIMESTAMP,
    closed_by       TEXT,
    CONSTRAINT server_events_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS server_events_state_index
    ON companies_schema.server_events (state, starts);
