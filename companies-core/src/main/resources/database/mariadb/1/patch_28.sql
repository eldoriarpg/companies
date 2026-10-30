CREATE TABLE IF NOT EXISTS server_events
(
    id              INT AUTO_INCREMENT NOT NULL,
    name            VARCHAR(64)        NOT NULL,
    kind            VARCHAR(32)        NOT NULL,
    value           DOUBLE             NOT NULL,
    scope_materials TEXT               NULL,
    scope_levels    TEXT               NULL,
    starts          DATETIME           NOT NULL,
    ends            DATETIME           NOT NULL,
    funding         VARCHAR(16)        NULL,
    account         VARCHAR(255)       NULL,
    budget          DOUBLE             NULL,
    spent           DOUBLE             NOT NULL DEFAULT 0,
    state           VARCHAR(16)        NOT NULL,
    template        VARCHAR(64)        NULL,
    created_by      VARCHAR(64)        NOT NULL,
    created         DATETIME           NOT NULL,
    closed          DATETIME           NULL,
    closed_by       VARCHAR(64)        NULL,
    CONSTRAINT server_events_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS server_events_state_index
    ON server_events (state, starts);
