CREATE TABLE market_history
(
    day       INTEGER NOT NULL,
    material  TEXT    NOT NULL,
    orders    INTEGER NOT NULL,
    items     INTEGER NOT NULL,
    avg_price FLOAT   NOT NULL,
    min_price FLOAT   NOT NULL,
    max_price FLOAT   NOT NULL,
    CONSTRAINT market_history_pk
        PRIMARY KEY (day, material)
);

CREATE INDEX market_history_material_index
    ON market_history (material, day);

CREATE TABLE market_history_state
(
    id               INTEGER NOT NULL
        CONSTRAINT market_history_state_pk
            PRIMARY KEY,
    recorded_through INTEGER NOT NULL
);
