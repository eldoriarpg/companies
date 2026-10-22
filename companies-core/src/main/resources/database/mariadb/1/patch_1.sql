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

CREATE TABLE orders_delivered_merged AS
SELECT id, worker_uuid, material, SUM(delivered) AS delivered
FROM orders_delivered
GROUP BY id, worker_uuid, material;

DELETE
FROM orders_delivered;

INSERT INTO orders_delivered(id, worker_uuid, material, delivered)
SELECT id, worker_uuid, material, delivered
FROM orders_delivered_merged;

DROP TABLE orders_delivered_merged;

ALTER TABLE orders_delivered
    ADD CONSTRAINT orders_delivered_uindex
        UNIQUE (id, worker_uuid, material(100));
