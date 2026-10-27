ALTER TABLE companies_schema.order_content
    ADD COLUMN IF NOT EXISTS criteria TEXT;

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
