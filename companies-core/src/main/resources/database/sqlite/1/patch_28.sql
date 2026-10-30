CREATE TABLE server_events
(
    id              INTEGER  NOT NULL
        CONSTRAINT server_events_pk
            PRIMARY KEY AUTOINCREMENT,
    name            TEXT     NOT NULL,
    kind            TEXT     NOT NULL,
    value           REAL     NOT NULL,
    scope_materials TEXT,
    scope_levels    TEXT,
    starts          datetime NOT NULL,
    ends            datetime NOT NULL,
    funding         TEXT,
    account         TEXT,
    budget          REAL,
    spent           REAL     NOT NULL DEFAULT 0,
    state           TEXT     NOT NULL,
    template        TEXT,
    created_by      TEXT     NOT NULL,
    created         datetime NOT NULL,
    closed          datetime,
    closed_by       TEXT
);

CREATE INDEX server_events_state_index
    ON server_events (state, starts);
