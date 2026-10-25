CREATE TABLE IF NOT EXISTS settings
(
    path       VARCHAR(64) NOT NULL,
    revision   INT         NOT NULL,
    content    MEDIUMTEXT  NOT NULL,
    hash       VARCHAR(64) NOT NULL,
    node_uid   BINARY(16)  NOT NULL,
    actor      VARCHAR(64) NOT NULL,
    changed_at DATETIME    NOT NULL,
    CONSTRAINT settings_pk
        PRIMARY KEY (path, revision)
);

CREATE TABLE IF NOT EXISTS settings_applied
(
    node_uid   BINARY(16)  NOT NULL,
    path       VARCHAR(64) NOT NULL,
    revision   INT         NOT NULL,
    applied_at DATETIME    NOT NULL,
    CONSTRAINT settings_applied_pk
        PRIMARY KEY (node_uid, path)
);
