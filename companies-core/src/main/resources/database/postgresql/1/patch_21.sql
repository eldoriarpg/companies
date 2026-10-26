CREATE TABLE IF NOT EXISTS companies_schema.system_order_decisions
(
    order_id   INTEGER          NOT NULL,
    rule       TEXT             NOT NULL,
    kind       TEXT             NOT NULL,
    decided_at TIMESTAMP        NOT NULL,
    account    TEXT,
    paid       DOUBLE PRECISION NOT NULL,
    record     TEXT             NOT NULL,
    CONSTRAINT system_order_decisions_pk
        PRIMARY KEY (order_id)
);

CREATE INDEX IF NOT EXISTS system_order_decisions_rule_index
    ON companies_schema.system_order_decisions (rule, decided_at);

CREATE INDEX IF NOT EXISTS system_order_decisions_decided_index
    ON companies_schema.system_order_decisions (decided_at);

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
                     JOIN companies_schema.orders o ON c.id = o.id
            WHERE s.state >= 200
              AND NOT c.exact
              AND o.owner_uuid IS DISTINCT FROM '\x0000000000000000000000000005e7e7'::bytea) c
      WHERE c.id < 100
      GROUP BY c.material) avg;
