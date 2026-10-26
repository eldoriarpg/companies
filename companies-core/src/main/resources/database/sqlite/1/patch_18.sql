CREATE TABLE settings_asset
(
    name       TEXT     NOT NULL,
    content    TEXT     NOT NULL,
    hash       TEXT     NOT NULL,
    node_uid   blob     NOT NULL,
    actor      TEXT     NOT NULL,
    changed_at datetime NOT NULL,
    CONSTRAINT settings_asset_pk
        PRIMARY KEY (name)
);
