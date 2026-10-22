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

CREATE OR REPLACE VIEW company_stats_view AS
SELECT c.id,
       c.name,
       c.founded,
       m.member_count,
       o.order_count - COALESCE(s.failed_orders, 0) AS order_count,
       o.price,
       o.amount
FROM companies c
         LEFT JOIN (SELECT company,
                           COUNT(1)    AS order_count,
                           SUM(price)  AS price,
                           SUM(amount) AS amount
                    FROM orders o
                             LEFT JOIN order_states os ON o.id = os.id
                             LEFT JOIN (SELECT id,
                                               SUM(amount) AS amount,
                                               SUM(price)  AS price
                                        FROM order_content
                                        GROUP BY id) oc
                                       ON o.id = oc.id
                    WHERE os.state >= 200
                    GROUP BY company) o ON c.id = o.company
         LEFT JOIN company_stats s ON c.id = s.id
         LEFT JOIN (SELECT id,
                           COUNT(1) AS member_count
                    FROM company_member
                    GROUP BY id) m ON c.id = m.id;
