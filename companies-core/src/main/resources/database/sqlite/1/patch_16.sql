CREATE TABLE settings
(
    path       TEXT     NOT NULL,
    revision   INTEGER  NOT NULL,
    content    TEXT     NOT NULL,
    hash       TEXT     NOT NULL,
    node_uid   blob     NOT NULL,
    actor      TEXT     NOT NULL,
    changed_at datetime NOT NULL,
    CONSTRAINT settings_pk
        PRIMARY KEY (path, revision)
);

CREATE TABLE settings_applied
(
    node_uid   blob     NOT NULL,
    path       TEXT     NOT NULL,
    revision   INTEGER  NOT NULL,
    applied_at datetime NOT NULL,
    CONSTRAINT settings_applied_pk
        PRIMARY KEY (node_uid, path)
);
