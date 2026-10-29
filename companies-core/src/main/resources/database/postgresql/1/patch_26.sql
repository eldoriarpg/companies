ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS prizes TEXT;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS held DOUBLE PRECISION NOT NULL DEFAULT 0;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS started TIMESTAMP;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS settled TIMESTAMP;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS refund DOUBLE PRECISION;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS refunded TIMESTAMP;

CREATE TABLE IF NOT EXISTS companies_schema.contest_entries
(
    offer_id      INTEGER          NOT NULL
        CONSTRAINT contest_entries_offer_fk
            REFERENCES companies_schema.event_offers
            ON DELETE CASCADE,
    company_id    INTEGER          NOT NULL,
    delivered     INTEGER          NOT NULL DEFAULT 0,
    paid          DOUBLE PRECISION NOT NULL DEFAULT 0,
    joined_at     TIMESTAMP        NOT NULL,
    left_at       TIMESTAMP,
    last_delivery TIMESTAMP,
    CONSTRAINT contest_entries_pk
        PRIMARY KEY (offer_id, company_id)
);

CREATE INDEX IF NOT EXISTS contest_entries_company_index
    ON companies_schema.contest_entries (company_id, left_at);

CREATE TABLE IF NOT EXISTS companies_schema.contest_deliveries
(
    id         SERIAL           NOT NULL,
    offer_id   INTEGER          NOT NULL
        CONSTRAINT contest_deliveries_offer_fk
            REFERENCES companies_schema.event_offers
            ON DELETE CASCADE,
    company_id INTEGER          NOT NULL,
    worker     BYTEA            NOT NULL,
    items      INTEGER          NOT NULL,
    gross      DOUBLE PRECISION NOT NULL,
    created    TIMESTAMP        NOT NULL,
    CONSTRAINT contest_deliveries_pk
        PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS contest_deliveries_offer_index
    ON companies_schema.contest_deliveries (offer_id, company_id);

CREATE TABLE IF NOT EXISTS companies_schema.contest_prizes
(
    offer_id   INTEGER          NOT NULL
        CONSTRAINT contest_prizes_offer_fk
            REFERENCES companies_schema.event_offers
            ON DELETE CASCADE,
    place      INTEGER          NOT NULL,
    company_id INTEGER,
    amount     DOUBLE PRECISION NOT NULL,
    state      TEXT             NOT NULL,
    settled    TIMESTAMP        NOT NULL,
    CONSTRAINT contest_prizes_pk
        PRIMARY KEY (offer_id, place)
);
