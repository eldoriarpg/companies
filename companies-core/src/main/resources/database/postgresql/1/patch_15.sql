CREATE TABLE IF NOT EXISTS companies_schema.saved_items
(
    id         SERIAL
        CONSTRAINT saved_items_pk
            PRIMARY KEY,
    owner_uuid bytea     NOT NULL,
    name       TEXT      NOT NULL,
    stack      TEXT      NOT NULL,
    created    TIMESTAMP NOT NULL,
    CONSTRAINT saved_items_uindex
        UNIQUE (owner_uuid, name)
);
