CREATE TABLE IF NOT EXISTS settings_asset
(
    name       VARCHAR(64) NOT NULL,
    content    MEDIUMTEXT  NOT NULL,
    hash       VARCHAR(64) NOT NULL,
    node_uid   BINARY(16)  NOT NULL,
    actor      VARCHAR(64) NOT NULL,
    changed_at DATETIME    NOT NULL,
    CONSTRAINT settings_asset_pk
        PRIMARY KEY (name)
);
