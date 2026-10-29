ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS prizes TEXT NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS held DOUBLE NOT NULL DEFAULT 0;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS started DATETIME NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS settled DATETIME NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS refund DOUBLE NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS refunded DATETIME NULL;

CREATE TABLE IF NOT EXISTS contest_entries
(
    offer_id      INT      NOT NULL,
    company_id    INT      NOT NULL,
    delivered     INT      NOT NULL DEFAULT 0,
    paid          DOUBLE   NOT NULL DEFAULT 0,
    joined_at     DATETIME NOT NULL,
    left_at       DATETIME NULL,
    last_delivery DATETIME NULL,
    CONSTRAINT contest_entries_pk
        PRIMARY KEY (offer_id, company_id),
    CONSTRAINT contest_entries_offer_fk
        FOREIGN KEY (offer_id) REFERENCES event_offers (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS contest_entries_company_index
    ON contest_entries (company_id, left_at);

CREATE TABLE IF NOT EXISTS contest_deliveries
(
    id         INT AUTO_INCREMENT NOT NULL,
    offer_id   INT                NOT NULL,
    company_id INT                NOT NULL,
    worker     BINARY(16)         NOT NULL,
    items      INT                NOT NULL,
    gross      DOUBLE             NOT NULL,
    created    DATETIME           NOT NULL,
    CONSTRAINT contest_deliveries_pk
        PRIMARY KEY (id),
    CONSTRAINT contest_deliveries_offer_fk
        FOREIGN KEY (offer_id) REFERENCES event_offers (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS contest_deliveries_offer_index
    ON contest_deliveries (offer_id, company_id);

CREATE TABLE IF NOT EXISTS contest_prizes
(
    offer_id   INT      NOT NULL,
    place      INT      NOT NULL,
    company_id INT      NULL,
    amount     DOUBLE   NOT NULL,
    state      VARCHAR(16) NOT NULL,
    settled    DATETIME NOT NULL,
    CONSTRAINT contest_prizes_pk
        PRIMARY KEY (offer_id, place),
    CONSTRAINT contest_prizes_offer_fk
        FOREIGN KEY (offer_id) REFERENCES event_offers (id)
            ON DELETE CASCADE
);
