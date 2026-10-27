ALTER TABLE order_content
    ADD COLUMN IF NOT EXISTS criteria TEXT NULL;

CREATE TABLE IF NOT EXISTS order_line_materials
(
    id              INT         NOT NULL,
    line            INT         NOT NULL,
    material        VARCHAR(64) NOT NULL,
    with_conditions BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT order_line_materials_pk
        PRIMARY KEY (id, line, material),
    CONSTRAINT order_line_materials_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);
