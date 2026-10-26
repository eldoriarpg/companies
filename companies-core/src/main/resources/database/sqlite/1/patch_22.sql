CREATE TABLE system_order_rest
(
    material TEXT     NOT NULL,
    rest_until datetime NOT NULL,
    CONSTRAINT system_order_rest_pk
        PRIMARY KEY (material)
);
