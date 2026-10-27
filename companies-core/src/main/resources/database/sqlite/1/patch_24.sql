ALTER TABLE order_content
    ADD COLUMN criteria TEXT;

CREATE TABLE order_line_materials
(
    id              INTEGER NOT NULL
        CONSTRAINT order_line_materials_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    line            INTEGER NOT NULL,
    material        TEXT    NOT NULL,
    with_conditions INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT order_line_materials_pk
        PRIMARY KEY (id, line, material)
);
