CREATE TABLE IF NOT EXISTS companies_schema.market_history
(
    day       INTEGER          NOT NULL,
    material  TEXT             NOT NULL,
    orders    INTEGER          NOT NULL,
    items     BIGINT           NOT NULL,
    avg_price DOUBLE PRECISION NOT NULL,
    min_price DOUBLE PRECISION NOT NULL,
    max_price DOUBLE PRECISION NOT NULL,
    CONSTRAINT market_history_pk
        PRIMARY KEY (day, material)
);

CREATE INDEX IF NOT EXISTS market_history_material_index
    ON companies_schema.market_history (material, day);

CREATE TABLE IF NOT EXISTS companies_schema.market_history_state
(
    id               INTEGER NOT NULL
        CONSTRAINT market_history_state_pk
            PRIMARY KEY,
    recorded_through INTEGER NOT NULL
);
