CREATE TABLE IF NOT EXISTS item_returns
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    worker_uuid BINARY(16)                            NOT NULL,
    material    TEXT                                  NOT NULL,
    stack       TEXT                                  NOT NULL,
    amount      INT                                   NOT NULL,
    created     TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL
);

CREATE INDEX IF NOT EXISTS item_returns_worker_uuid_index
    ON item_returns (worker_uuid);
