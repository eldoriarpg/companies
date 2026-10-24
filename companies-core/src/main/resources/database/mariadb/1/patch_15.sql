CREATE TABLE IF NOT EXISTS saved_items
(
    id         INT AUTO_INCREMENT
        PRIMARY KEY,
    owner_uuid BINARY(16)  NOT NULL,
    name       VARCHAR(32) NOT NULL,
    stack      TEXT        NOT NULL,
    created    DATETIME    NOT NULL,
    CONSTRAINT saved_items_uindex
        UNIQUE (owner_uuid, name)
);
