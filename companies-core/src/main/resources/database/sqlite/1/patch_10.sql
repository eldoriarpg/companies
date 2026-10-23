CREATE TABLE node_messages
(
    id          INTEGER  NOT NULL
        CONSTRAINT node_messages_pk
            PRIMARY KEY AUTOINCREMENT,
    created     datetime NOT NULL,
    origin_node blob     NOT NULL,
    type        TEXT     NOT NULL,
    payload     TEXT     NOT NULL
);

CREATE INDEX node_messages_created_index
    ON node_messages (created);
