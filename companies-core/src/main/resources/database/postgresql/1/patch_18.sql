CREATE TABLE IF NOT EXISTS companies_schema.settings_asset
(
    name       TEXT      NOT NULL,
    content    TEXT      NOT NULL,
    hash       TEXT      NOT NULL,
    node_uid   bytea     NOT NULL,
    actor      TEXT      NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT settings_asset_pk
        PRIMARY KEY (name)
);
