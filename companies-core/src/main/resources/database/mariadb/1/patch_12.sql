CREATE TABLE IF NOT EXISTS market_history
(
    day       INT          NOT NULL,
    material  VARCHAR(255) NOT NULL,
    orders    INT          NOT NULL,
    items     BIGINT       NOT NULL,
    avg_price DOUBLE       NOT NULL,
    min_price DOUBLE       NOT NULL,
    max_price DOUBLE       NOT NULL,
    PRIMARY KEY (day, material)
);

CREATE INDEX IF NOT EXISTS market_history_material_index
    ON market_history (material, day);
