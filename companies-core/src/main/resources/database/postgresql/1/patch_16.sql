CREATE TABLE IF NOT EXISTS companies_schema.settings
(
    path       TEXT      NOT NULL,
    revision   INT       NOT NULL,
    content    TEXT      NOT NULL,
    hash       TEXT      NOT NULL,
    node_uid   bytea     NOT NULL,
    actor      TEXT      NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT settings_pk
        PRIMARY KEY (path, revision)
);

CREATE TABLE IF NOT EXISTS companies_schema.settings_applied
(
    node_uid   bytea     NOT NULL,
    path       TEXT      NOT NULL,
    revision   INT       NOT NULL,
    applied_at TIMESTAMP NOT NULL,
    CONSTRAINT settings_applied_pk
        PRIMARY KEY (node_uid, path)
);
