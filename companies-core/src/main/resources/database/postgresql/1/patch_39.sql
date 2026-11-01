DROP MATERIALIZED VIEW IF EXISTS companies_schema.material_price;

CREATE MATERIALIZED VIEW companies_schema.material_price AS
SELECT material, avg_price, min_price, max_price, orders
FROM (SELECT c.material,
             AVG(c.price / c.amount) AS avg_price,
             MIN(c.price / c.amount) AS min_price,
             MAX(c.price / c.amount) AS max_price,
             COUNT(*)                AS orders
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
              AND c.criteria IS NULL
              AND o.owner_uuid IS DISTINCT FROM '\x0000000000000000000000000005e7e7'::bytea) c
      WHERE c.id < 100
      GROUP BY c.material) avg;

CREATE TABLE IF NOT EXISTS companies_schema.shop_trade_day
(
    day      INTEGER          NOT NULL,
    source   TEXT             NOT NULL,
    material TEXT             NOT NULL,
    side     TEXT             NOT NULL,
    origin   TEXT             NOT NULL,
    trades   INTEGER          NOT NULL,
    items    BIGINT           NOT NULL,
    total    DOUBLE PRECISION NOT NULL,
    min_unit DOUBLE PRECISION NOT NULL,
    max_unit DOUBLE PRECISION NOT NULL,
    CONSTRAINT shop_trade_day_pk
        PRIMARY KEY (day, source, material, side, origin)
);

CREATE TABLE IF NOT EXISTS companies_schema.shop_quote
(
    source      TEXT             NOT NULL,
    node        BYTEA            NOT NULL,
    material    TEXT             NOT NULL,
    side        TEXT             NOT NULL,
    origin      TEXT             NOT NULL,
    quotes      INTEGER          NOT NULL,
    items       BIGINT           NOT NULL,
    median_unit DOUBLE PRECISION NOT NULL,
    min_unit    DOUBLE PRECISION NOT NULL,
    max_unit    DOUBLE PRECISION NOT NULL,
    sampled_at  TIMESTAMP        NOT NULL,
    CONSTRAINT shop_quote_pk
        PRIMARY KEY (source, node, material, side, origin)
);

CREATE INDEX IF NOT EXISTS shop_quote_node_index
    ON companies_schema.shop_quote (node);

CREATE TABLE IF NOT EXISTS companies_schema.material_baseline
(
    material      TEXT             NOT NULL
        CONSTRAINT material_baseline_pk
            PRIMARY KEY,
    sell_unit     DOUBLE PRECISION,
    buy_unit      DOUBLE PRECISION,
    baseline_unit DOUBLE PRECISION NOT NULL,
    basis         TEXT             NOT NULL,
    sources       TEXT             NOT NULL DEFAULT '',
    updated_at    TIMESTAMP        NOT NULL
);
