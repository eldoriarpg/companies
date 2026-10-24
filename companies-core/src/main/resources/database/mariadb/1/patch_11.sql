CREATE TABLE IF NOT EXISTS dashboard_key
(
    id         INT          NOT NULL
        PRIMARY KEY,
    secret     VARCHAR(512) NOT NULL,
    kind       VARCHAR(32)  NOT NULL,
    refresh_at DATETIME     NULL,
    checked_at DATETIME     NULL,
    seed_hash  VARCHAR(64)  NULL,
    updated    DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS dashboard_sessions
(
    id           VARCHAR(128) NOT NULL
        PRIMARY KEY,
    player_uuid  BINARY(16)   NOT NULL,
    player_name  VARCHAR(64)  NOT NULL,
    node_uuid    BINARY(16)   NOT NULL,
    created      DATETIME     NOT NULL,
    code_expires DATETIME     NOT NULL,
    last_active  DATETIME     NOT NULL,
    opened       DATETIME     NULL,
    device       VARCHAR(256) NULL,
    permissions  TEXT         NOT NULL
);

CREATE INDEX IF NOT EXISTS dashboard_sessions_player_index
    ON dashboard_sessions (player_uuid);
