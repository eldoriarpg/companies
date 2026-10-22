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

CREATE OR REPLACE VIEW companies_schema.company_stats_view AS
SELECT c.id,
       c.name,
       c.founded,
       m.member_count,
       o.order_count - COALESCE(s.failed_orders, 0) AS order_count,
       o.price,
       o.amount
FROM companies_schema.companies c
         LEFT JOIN (SELECT company,
                           COUNT(1)    AS order_count,
                           SUM(price)  AS price,
                           SUM(amount) AS amount
                    FROM companies_schema.orders o
                             LEFT JOIN companies_schema.order_states os ON o.id = os.id
                             LEFT JOIN (SELECT id,
                                               SUM(amount) AS amount,
                                               SUM(price)  AS price
                                        FROM companies_schema.order_content
                                        GROUP BY id) oc
                                       ON o.id = oc.id
                    WHERE os.state >= 200
                    GROUP BY company) o ON c.id = o.company
         LEFT JOIN companies_schema.company_stats s ON c.id = s.id
         LEFT JOIN (SELECT id,
                           COUNT(1) AS member_count
                    FROM companies_schema.company_member
                    GROUP BY id) m ON c.id = m.id;
