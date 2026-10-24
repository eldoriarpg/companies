CREATE TABLE dashboard_key
(
    id         INTEGER  NOT NULL
        CONSTRAINT dashboard_key_pk
            PRIMARY KEY,
    secret     TEXT     NOT NULL,
    kind       TEXT     NOT NULL,
    refresh_at datetime,
    checked_at datetime,
    seed_hash  TEXT,
    updated    datetime NOT NULL
);

CREATE TABLE dashboard_sessions
(
    id           TEXT     NOT NULL
        CONSTRAINT dashboard_sessions_pk
            PRIMARY KEY,
    player_uuid  blob     NOT NULL,
    player_name  TEXT     NOT NULL,
    node_uuid    blob     NOT NULL,
    created      datetime NOT NULL,
    code_expires datetime NOT NULL,
    last_active  datetime NOT NULL,
    opened       datetime,
    device       TEXT,
    permissions  TEXT     NOT NULL
);

CREATE INDEX dashboard_sessions_player_index
    ON dashboard_sessions (player_uuid);
