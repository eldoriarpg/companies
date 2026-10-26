CREATE TABLE IF NOT EXISTS system_order_rest
(
    material VARCHAR(64) NOT NULL,
    rest_until DATETIME    NOT NULL,
    CONSTRAINT system_order_rest_pk
        PRIMARY KEY (material)
);
