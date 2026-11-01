ALTER TABLE material_price
    ADD COLUMN IF NOT EXISTS orders INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS shop_trade_day
(
    day      INT          NOT NULL,
    source   VARCHAR(64)  NOT NULL,
    material VARCHAR(255) NOT NULL,
    side     VARCHAR(16)  NOT NULL,
    origin   VARCHAR(16)  NOT NULL,
    trades   INT          NOT NULL,
    items    BIGINT       NOT NULL,
    total    DOUBLE       NOT NULL,
    min_unit DOUBLE       NOT NULL,
    max_unit DOUBLE       NOT NULL,
    PRIMARY KEY (day, source, material, side, origin)
);

CREATE TABLE IF NOT EXISTS shop_quote
(
    source      VARCHAR(64)  NOT NULL,
    node        BINARY(16)   NOT NULL,
    material    VARCHAR(255) NOT NULL,
    side        VARCHAR(16)  NOT NULL,
    origin      VARCHAR(16)  NOT NULL,
    quotes      INT          NOT NULL,
    items       BIGINT       NOT NULL,
    median_unit DOUBLE       NOT NULL,
    min_unit    DOUBLE       NOT NULL,
    max_unit    DOUBLE       NOT NULL,
    sampled_at  DATETIME     NOT NULL,
    PRIMARY KEY (source, node, material, side, origin)
);

CREATE INDEX IF NOT EXISTS shop_quote_node_index
    ON shop_quote (node);

CREATE TABLE IF NOT EXISTS material_baseline
(
    material      VARCHAR(255) NOT NULL
        PRIMARY KEY,
    sell_unit     DOUBLE,
    buy_unit      DOUBLE,
    baseline_unit DOUBLE       NOT NULL,
    basis         VARCHAR(16)  NOT NULL,
    updated_at    DATETIME     NOT NULL
);
