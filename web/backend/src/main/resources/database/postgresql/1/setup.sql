CREATE TABLE web_schema.server_token
(
    id         TEXT PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    token_hash TEXT        NOT NULL UNIQUE,
    created    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE web_schema.server
(
    id              TEXT PRIMARY KEY,
    name            TEXT,
    icon_hash       TEXT,
    first_seen      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen       TIMESTAMPTZ NOT NULL DEFAULT now(),
    disconnected_at TIMESTAMPTZ
);

CREATE TABLE web_schema.server_node
(
    server_id TEXT        NOT NULL REFERENCES web_schema.server (id) ON DELETE CASCADE,
    uid       UUID        NOT NULL,
    role      TEXT        NOT NULL,
    plugin    TEXT        NOT NULL,
    protocol  INT         NOT NULL,
    last_seen TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (server_id, uid)
);

CREATE TABLE web_schema.icon
(
    hash    TEXT PRIMARY KEY,
    png     BYTEA       NOT NULL,
    created TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE web_schema.session
(
    id           TEXT PRIMARY KEY,
    server_id    TEXT        NOT NULL REFERENCES web_schema.server (id) ON DELETE CASCADE,
    player       UUID        NOT NULL,
    player_name  TEXT        NOT NULL,
    node         UUID        NOT NULL,
    state        TEXT        NOT NULL,
    short_id     TEXT UNIQUE,
    code_hash    TEXT,
    attempts     INT         NOT NULL DEFAULT 0,
    code_expires TIMESTAMPTZ,
    cookie_hash  TEXT UNIQUE,
    device       TEXT,
    created      TIMESTAMPTZ NOT NULL,
    bound_at     TIMESTAMPTZ,
    last_access  TIMESTAMPTZ NOT NULL
);

CREATE INDEX session_server_player ON web_schema.session (server_id, player);
