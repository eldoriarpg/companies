CREATE TABLE IF NOT EXISTS companies_schema.node_messages
(
    id          BIGSERIAL
        CONSTRAINT node_messages_pk
            PRIMARY KEY,
    created     TIMESTAMP NOT NULL,
    origin_node bytea     NOT NULL,
    type        TEXT      NOT NULL,
    payload     TEXT      NOT NULL
);

CREATE INDEX IF NOT EXISTS node_messages_created_index
    ON companies_schema.node_messages (created);
