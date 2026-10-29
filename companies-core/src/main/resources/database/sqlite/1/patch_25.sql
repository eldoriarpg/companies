CREATE TABLE event_offers
(
    id          INTEGER  NOT NULL
        CONSTRAINT event_offers_pk
            PRIMARY KEY AUTOINCREMENT,
    kind        TEXT     NOT NULL,
    name        TEXT     NOT NULL,
    event_id    INTEGER,
    material    TEXT,
    criteria    TEXT,
    resolve     TEXT,
    item        TEXT,
    label       TEXT     NOT NULL,
    amount      INTEGER  NOT NULL,
    unit_price  REAL     NOT NULL,
    chunk_size  INTEGER  NOT NULL,
    open_chunks INTEGER  NOT NULL,
    source      TEXT     NOT NULL,
    account     TEXT,
    budget      REAL     NOT NULL,
    spent       REAL     NOT NULL DEFAULT 0,
    delivered   INTEGER  NOT NULL DEFAULT 0,
    starts      datetime NOT NULL,
    ends        datetime NOT NULL,
    state       TEXT     NOT NULL,
    created_by  TEXT     NOT NULL,
    created     datetime NOT NULL,
    closed      datetime
);

CREATE INDEX event_offers_state_index
    ON event_offers (state);

CREATE TABLE event_offer_chunks
(
    order_id    INTEGER  NOT NULL
        CONSTRAINT event_offer_chunks_pk
            PRIMARY KEY,
    offer_id    INTEGER  NOT NULL
        CONSTRAINT event_offer_chunks_offer_fk
            REFERENCES event_offers
            ON DELETE CASCADE,
    amount      INTEGER  NOT NULL,
    delivered   INTEGER  NOT NULL DEFAULT 0,
    paid        REAL     NOT NULL,
    account     TEXT,
    state       TEXT     NOT NULL,
    company     INTEGER,
    solo_worker blob,
    placed      datetime NOT NULL,
    ended       datetime
);

CREATE INDEX event_offer_chunks_offer_index
    ON event_offer_chunks (offer_id, state);

ALTER TABLE orders
    ADD COLUMN offer_id INTEGER;

CREATE INDEX orders_offer_index
    ON orders (offer_id);
