ALTER TABLE companies_schema.order_content
    ADD COLUMN IF NOT EXISTS criteria TEXT;

CREATE TABLE IF NOT EXISTS companies_schema.order_line_materials
(
    id              INTEGER NOT NULL
        CONSTRAINT order_line_materials_orders_id_fk
            REFERENCES companies_schema.orders
            ON DELETE CASCADE,
    line            INTEGER NOT NULL,
    material        TEXT    NOT NULL,
    with_conditions BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT order_line_materials_pk
        PRIMARY KEY (id, line, material)
);

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
              AND c.criteria IS NULL
              AND o.owner_uuid IS DISTINCT FROM '\x0000000000000000000000000005e7e7'::bytea) c
      WHERE c.id < 100
      GROUP BY c.material) avg;
