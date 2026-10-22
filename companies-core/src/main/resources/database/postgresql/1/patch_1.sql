CREATE TABLE IF NOT EXISTS companies_schema.item_returns
(
    id          SERIAL
        CONSTRAINT item_returns_pk
            PRIMARY KEY,
    worker_uuid bytea                   NOT NULL,
    material    TEXT                    NOT NULL,
    stack       TEXT                    NOT NULL,
    amount      INTEGER                 NOT NULL,
    created     TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS item_returns_worker_uuid_index
    ON companies_schema.item_returns (worker_uuid);
