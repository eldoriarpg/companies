CREATE TABLE saved_items
(
    id         INTEGER  NOT NULL
        CONSTRAINT saved_items_pk
            PRIMARY KEY AUTOINCREMENT,
    owner_uuid blob     NOT NULL,
    name       TEXT     NOT NULL,
    stack      TEXT     NOT NULL,
    created    datetime NOT NULL,
    CONSTRAINT saved_items_uindex
        UNIQUE (owner_uuid, name)
);
