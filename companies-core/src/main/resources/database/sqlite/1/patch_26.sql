ALTER TABLE event_offers
    ADD COLUMN prizes TEXT;

ALTER TABLE event_offers
    ADD COLUMN held REAL NOT NULL DEFAULT 0;

ALTER TABLE event_offers
    ADD COLUMN started datetime;

ALTER TABLE event_offers
    ADD COLUMN settled datetime;

ALTER TABLE event_offers
    ADD COLUMN refund REAL;

ALTER TABLE event_offers
    ADD COLUMN refunded datetime;

CREATE TABLE contest_entries
(
    offer_id      INTEGER  NOT NULL
        CONSTRAINT contest_entries_offer_fk
            REFERENCES event_offers
            ON DELETE CASCADE,
    company_id    INTEGER  NOT NULL,
    delivered     INTEGER  NOT NULL DEFAULT 0,
    paid          REAL     NOT NULL DEFAULT 0,
    joined_at     datetime NOT NULL,
    left_at       datetime,
    last_delivery datetime,
    CONSTRAINT contest_entries_pk
        PRIMARY KEY (offer_id, company_id)
);

CREATE INDEX contest_entries_company_index
    ON contest_entries (company_id, left_at);

CREATE TABLE contest_deliveries
(
    id         INTEGER  NOT NULL
        CONSTRAINT contest_deliveries_pk
            PRIMARY KEY AUTOINCREMENT,
    offer_id   INTEGER  NOT NULL
        CONSTRAINT contest_deliveries_offer_fk
            REFERENCES event_offers
            ON DELETE CASCADE,
    company_id INTEGER  NOT NULL,
    worker     blob     NOT NULL,
    items      INTEGER  NOT NULL,
    gross      REAL     NOT NULL,
    created    datetime NOT NULL
);

CREATE INDEX contest_deliveries_offer_index
    ON contest_deliveries (offer_id, company_id);

CREATE TABLE contest_prizes
(
    offer_id   INTEGER  NOT NULL
        CONSTRAINT contest_prizes_offer_fk
            REFERENCES event_offers
            ON DELETE CASCADE,
    place      INTEGER  NOT NULL,
    company_id INTEGER,
    amount     REAL     NOT NULL,
    state      TEXT     NOT NULL,
    settled    datetime NOT NULL,
    CONSTRAINT contest_prizes_pk
        PRIMARY KEY (offer_id, place)
);
