CREATE TABLE IF NOT EXISTS companies_schema.dashboard_key
(
    id         INTEGER   NOT NULL
        CONSTRAINT dashboard_key_pk
            PRIMARY KEY,
    secret     TEXT      NOT NULL,
    kind       TEXT      NOT NULL,
    refresh_at TIMESTAMP,
    checked_at TIMESTAMP,
    seed_hash  TEXT,
    updated    TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS companies_schema.dashboard_sessions
(
    id           TEXT      NOT NULL
        CONSTRAINT dashboard_sessions_pk
            PRIMARY KEY,
    player_uuid  bytea     NOT NULL,
    player_name  TEXT      NOT NULL,
    node_uuid    bytea     NOT NULL,
    created      TIMESTAMP NOT NULL,
    code_expires TIMESTAMP NOT NULL,
    last_active  TIMESTAMP NOT NULL,
    opened       TIMESTAMP,
    device       TEXT,
    permissions  TEXT      NOT NULL
);

CREATE INDEX IF NOT EXISTS dashboard_sessions_player_index
    ON companies_schema.dashboard_sessions (player_uuid);
