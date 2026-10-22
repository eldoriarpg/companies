CREATE TABLE item_returns
(
    id          INTEGER                            NOT NULL
        CONSTRAINT item_returns_pk
            PRIMARY KEY AUTOINCREMENT,
    worker_uuid blob                               NOT NULL,
    material    TEXT                               NOT NULL,
    stack       TEXT                               NOT NULL,
    amount      INTEGER                            NOT NULL,
    created     datetime DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX item_returns_worker_uuid_index
    ON item_returns (worker_uuid);
