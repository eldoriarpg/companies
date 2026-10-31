CREATE TABLE area_offers
(
    id          INTEGER  NOT NULL
        CONSTRAINT area_offers_pk
            PRIMARY KEY AUTOINCREMENT,
    provider    TEXT     NOT NULL,
    ref         TEXT     NOT NULL,
    world       TEXT     NOT NULL,
    node        blob     NOT NULL,
    kind        TEXT     NOT NULL,
    price       REAL     NOT NULL,
    period_days INTEGER  NOT NULL DEFAULT 0,
    keeper      blob,
    created     datetime NOT NULL,
    CONSTRAINT area_offers_ref_uindex
        UNIQUE (provider, ref)
);

ALTER TABLE company_areas
    ADD COLUMN offer_id INTEGER;

ALTER TABLE company_areas
    ADD COLUMN renew INTEGER NOT NULL DEFAULT 1;

ALTER TABLE company_areas
    ADD COLUMN rent_warned INTEGER NOT NULL DEFAULT 0;

ALTER TABLE company_areas
    ADD COLUMN rent_ended INTEGER NOT NULL DEFAULT 0;
