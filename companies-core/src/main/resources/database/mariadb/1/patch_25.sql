CREATE TABLE IF NOT EXISTS event_offers
(
    id          INT AUTO_INCREMENT NOT NULL,
    kind        VARCHAR(16)        NOT NULL,
    name        VARCHAR(64)        NOT NULL,
    event_id    INT                NULL,
    material    VARCHAR(64)        NULL,
    criteria    TEXT               NULL,
    resolve     VARCHAR(8)         NULL,
    item        TEXT               NULL,
    label       TEXT               NOT NULL,
    amount      INT                NOT NULL,
    unit_price  DOUBLE             NOT NULL,
    chunk_size  INT                NOT NULL,
    open_chunks INT                NOT NULL,
    source      VARCHAR(16)        NOT NULL,
    account     VARCHAR(255)       NULL,
    budget      DOUBLE             NOT NULL,
    spent       DOUBLE             NOT NULL DEFAULT 0,
    delivered   INT                NOT NULL DEFAULT 0,
    starts      DATETIME           NOT NULL,
    ends        DATETIME           NOT NULL,
    state       VARCHAR(16)        NOT NULL,
    created_by  VARCHAR(64)        NOT NULL,
    created     DATETIME           NOT NULL,
    closed      DATETIME           NULL,
    CONSTRAINT event_offers_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_offers_state_index
    ON event_offers (state);

CREATE TABLE IF NOT EXISTS event_offer_chunks
(
    order_id    INT          NOT NULL,
    offer_id    INT          NOT NULL,
    amount      INT          NOT NULL,
    delivered   INT          NOT NULL DEFAULT 0,
    paid        DOUBLE       NOT NULL,
    account     VARCHAR(255) NULL,
    state       VARCHAR(16)  NOT NULL,
    company     INT          NULL,
    solo_worker BINARY(16)   NULL,
    placed      DATETIME     NOT NULL,
    ended       DATETIME     NULL,
    CONSTRAINT event_offer_chunks_pk
        PRIMARY KEY (order_id),
    CONSTRAINT event_offer_chunks_offer_fk
        FOREIGN KEY (offer_id) REFERENCES event_offers (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS event_offer_chunks_offer_index
    ON event_offer_chunks (offer_id, state);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS offer_id INT NULL;

CREATE INDEX IF NOT EXISTS orders_offer_index
    ON orders (offer_id);
