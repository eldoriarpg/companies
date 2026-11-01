ALTER TABLE material_price
    ADD COLUMN orders INTEGER NOT NULL DEFAULT 0;

CREATE TABLE shop_trade_day
(
    day      INTEGER NOT NULL,
    source   TEXT    NOT NULL,
    material TEXT    NOT NULL,
    side     TEXT    NOT NULL,
    origin   TEXT    NOT NULL,
    trades   INTEGER NOT NULL,
    items    INTEGER NOT NULL,
    total    FLOAT   NOT NULL,
    min_unit FLOAT   NOT NULL,
    max_unit FLOAT   NOT NULL,
    CONSTRAINT shop_trade_day_pk
        PRIMARY KEY (day, source, material, side, origin)
);

CREATE TABLE shop_quote
(
    source      TEXT     NOT NULL,
    node        BLOB     NOT NULL,
    material    TEXT     NOT NULL,
    side        TEXT     NOT NULL,
    origin      TEXT     NOT NULL,
    quotes      INTEGER  NOT NULL,
    items       INTEGER  NOT NULL,
    median_unit FLOAT    NOT NULL,
    min_unit    FLOAT    NOT NULL,
    max_unit    FLOAT    NOT NULL,
    sampled_at  datetime NOT NULL,
    CONSTRAINT shop_quote_pk
        PRIMARY KEY (source, node, material, side, origin)
);

CREATE INDEX shop_quote_node_index
    ON shop_quote (node);

CREATE TABLE material_baseline
(
    material      TEXT     NOT NULL
        CONSTRAINT material_baseline_pk
            PRIMARY KEY,
    sell_unit     FLOAT,
    buy_unit      FLOAT,
    baseline_unit FLOAT    NOT NULL,
    basis         TEXT     NOT NULL,
    sources       TEXT     NOT NULL DEFAULT '',
    updated_at    datetime NOT NULL
);
