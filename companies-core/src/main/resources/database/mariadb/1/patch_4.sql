ALTER TABLE order_content
    ADD COLUMN IF NOT EXISTS line INT DEFAULT 0 NOT NULL;

ALTER TABLE order_content
    ADD COLUMN IF NOT EXISTS exact BOOLEAN DEFAULT FALSE NOT NULL;

CREATE TABLE order_content_lines AS
SELECT id,
       material,
       MIN(stack)                                             AS stack,
       SUM(amount)                                            AS amount,
       SUM(price)                                             AS price,
       ROW_NUMBER() OVER (PARTITION BY id ORDER BY material) - 1 AS line
FROM order_content
GROUP BY id, material;

DELETE
FROM order_content;

INSERT INTO order_content(id, line, material, stack, exact, amount, price)
SELECT id, line, material, stack, FALSE, amount, price
FROM order_content_lines;

DROP TABLE order_content_lines;

ALTER TABLE order_content
    ADD CONSTRAINT order_content_id_line_uindex
        UNIQUE (id, line);

CREATE TABLE IF NOT EXISTS order_deliveries
(
    id          INT AUTO_INCREMENT
        PRIMARY KEY,
    order_id    INT                                   NOT NULL,
    line        INT                                   NOT NULL,
    worker_uuid BINARY(16)                            NOT NULL,
    stack       TEXT                                  NOT NULL,
    amount      INT                                   NOT NULL,
    created     TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL,
    CONSTRAINT order_deliveries_orders_id_fk
        FOREIGN KEY (order_id) REFERENCES orders (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS order_deliveries_order_line_index
    ON order_deliveries (order_id, line);

CREATE INDEX IF NOT EXISTS order_deliveries_order_worker_index
    ON order_deliveries (order_id, worker_uuid);

INSERT INTO order_deliveries(order_id, line, worker_uuid, stack, amount)
SELECT d.id, c.line, d.worker_uuid, c.stack, SUM(d.delivered)
FROM orders_delivered d
         JOIN order_content c
              ON c.id = d.id
                  AND c.material = d.material
WHERE d.delivered > 0
GROUP BY d.id, c.line, d.worker_uuid, c.stack
ORDER BY d.id, c.line;

DROP TABLE orders_delivered;
