CREATE TABLE IF NOT EXISTS companies_schema.system_order_rest
(
    material TEXT      NOT NULL,
    rest_until TIMESTAMP NOT NULL,
    CONSTRAINT system_order_rest_pk
        PRIMARY KEY (material)
);
