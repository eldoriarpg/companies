CREATE TABLE IF NOT EXISTS node_messages
(
    id          BIGINT AUTO_INCREMENT
        PRIMARY KEY,
    created     DATETIME    NOT NULL,
    origin_node BINARY(16)  NOT NULL,
    type        VARCHAR(64) NOT NULL,
    payload     MEDIUMTEXT  NOT NULL
);

CREATE INDEX IF NOT EXISTS node_messages_created_index
    ON node_messages (created);
