CREATE TABLE IF NOT EXISTS companies_schema.event_offers
(
    id          SERIAL           NOT NULL,
    kind        TEXT             NOT NULL,
    name        TEXT             NOT NULL,
    event_id    INTEGER,
    material    TEXT,
    criteria    TEXT,
    resolve     TEXT,
    item        TEXT,
    label       TEXT             NOT NULL,
    amount      INTEGER          NOT NULL,
    unit_price  DOUBLE PRECISION NOT NULL,
    chunk_size  INTEGER          NOT NULL,
    open_chunks INTEGER          NOT NULL,
    source      TEXT             NOT NULL,
    account     TEXT,
    budget      DOUBLE PRECISION NOT NULL,
    spent       DOUBLE PRECISION NOT NULL DEFAULT 0,
    delivered   INTEGER          NOT NULL DEFAULT 0,
    starts      TIMESTAMP        NOT NULL,
    ends        TIMESTAMP        NOT NULL,
    state       TEXT             NOT NULL,
    created_by  TEXT             NOT NULL,
    created     TIMESTAMP        NOT NULL,
    closed      TIMESTAMP,
    CONSTRAINT event_offers_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_offers_state_index
    ON companies_schema.event_offers (state);

CREATE TABLE IF NOT EXISTS companies_schema.event_offer_chunks
(
    order_id    INTEGER          NOT NULL,
    offer_id    INTEGER          NOT NULL
        CONSTRAINT event_offer_chunks_offer_fk
            REFERENCES companies_schema.event_offers
            ON DELETE CASCADE,
    amount      INTEGER          NOT NULL,
    delivered   INTEGER          NOT NULL DEFAULT 0,
    paid        DOUBLE PRECISION NOT NULL,
    account     TEXT,
    state       TEXT             NOT NULL,
    company     INTEGER,
    solo_worker BYTEA,
    placed      TIMESTAMP        NOT NULL,
    ended       TIMESTAMP,
    CONSTRAINT event_offer_chunks_pk
        PRIMARY KEY (order_id)
);

CREATE INDEX IF NOT EXISTS event_offer_chunks_offer_index
    ON companies_schema.event_offer_chunks (offer_id, state);

ALTER TABLE companies_schema.orders
    ADD COLUMN IF NOT EXISTS offer_id INTEGER;

CREATE INDEX IF NOT EXISTS orders_offer_index
    ON companies_schema.orders (offer_id);
