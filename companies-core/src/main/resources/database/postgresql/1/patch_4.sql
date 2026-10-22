ALTER TABLE companies_schema.order_content
    ADD COLUMN IF NOT EXISTS line INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE companies_schema.order_content
    ADD COLUMN IF NOT EXISTS exact BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE companies_schema.order_content
    DROP CONSTRAINT IF EXISTS order_content_pk;

CREATE TABLE companies_schema.order_content_lines AS
SELECT id,
       material,
       MIN(stack)                                             AS stack,
       SUM(amount)                                            AS amount,
       SUM(price)                                             AS price,
       ROW_NUMBER() OVER (PARTITION BY id ORDER BY material) - 1 AS line
FROM companies_schema.order_content
GROUP BY id, material;

DELETE
FROM companies_schema.order_content;

INSERT INTO companies_schema.order_content(id, line, material, stack, exact, amount, price)
SELECT id, line, material, stack, FALSE, amount, price
FROM companies_schema.order_content_lines;

DROP TABLE companies_schema.order_content_lines;

ALTER TABLE companies_schema.order_content
    ADD CONSTRAINT order_content_id_line_uindex
        UNIQUE (id, line);

CREATE TABLE IF NOT EXISTS companies_schema.order_deliveries
(
    id          SERIAL
        CONSTRAINT order_deliveries_pk
            PRIMARY KEY,
    order_id    INTEGER                 NOT NULL
        CONSTRAINT order_deliveries_orders_id_fk
            REFERENCES companies_schema.orders
            ON DELETE CASCADE,
    line        INTEGER                 NOT NULL,
    worker_uuid bytea                   NOT NULL,
    stack       TEXT                    NOT NULL,
    amount      INTEGER                 NOT NULL,
    created     TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS order_deliveries_order_line_index
    ON companies_schema.order_deliveries (order_id, line);

CREATE INDEX IF NOT EXISTS order_deliveries_order_worker_index
    ON companies_schema.order_deliveries (order_id, worker_uuid);

INSERT INTO companies_schema.order_deliveries(order_id, line, worker_uuid, stack, amount)
SELECT d.id, c.line, d.worker_uuid, c.stack, SUM(d.delivered)
FROM companies_schema.orders_delivered d
         JOIN companies_schema.order_content c
              ON c.id = d.id
                  AND c.material = d.material
WHERE d.delivered > 0
GROUP BY d.id, c.line, d.worker_uuid, c.stack
ORDER BY d.id, c.line;

DROP TABLE companies_schema.orders_delivered;

DROP MATERIALIZED VIEW IF EXISTS companies_schema.material_price;

CREATE MATERIALIZED VIEW companies_schema.material_price AS
SELECT material, avg_price, min_price, max_price
FROM (SELECT c.material,
             AVG(c.price / c.amount) AS avg_price,
             MIN(c.price / c.amount) AS min_price,
             MAX(c.price / c.amount) AS max_price
      FROM (SELECT ROW_NUMBER() OVER (PARTITION BY material ORDER BY last_update DESC) AS id,
                   material,
                   amount,
                   price,
                   last_update
            FROM companies_schema.order_content c
                     LEFT JOIN companies_schema.order_states s ON c.id = s.id
            WHERE s.state >= 200
              AND NOT c.exact) c
      WHERE c.id < 100
      GROUP BY c.material) avg;
