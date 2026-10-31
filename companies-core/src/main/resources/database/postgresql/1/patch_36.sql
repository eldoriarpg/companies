CREATE TABLE IF NOT EXISTS companies_schema.area_offers
(
    id          SERIAL           NOT NULL
        CONSTRAINT area_offers_pk
            PRIMARY KEY,
    provider    VARCHAR(32)      NOT NULL,
    ref         VARCHAR(255)     NOT NULL,
    world       VARCHAR(64)      NOT NULL,
    node        BYTEA            NOT NULL,
    kind        VARCHAR(8)       NOT NULL,
    price       DOUBLE PRECISION NOT NULL,
    period_days INTEGER          NOT NULL DEFAULT 0,
    keeper      BYTEA,
    created     TIMESTAMP        NOT NULL,
    CONSTRAINT area_offers_ref_uindex
        UNIQUE (provider, ref)
);

ALTER TABLE companies_schema.company_areas
    ADD COLUMN IF NOT EXISTS offer_id    INTEGER,
    ADD COLUMN IF NOT EXISTS renew       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS rent_warned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rent_ended  BOOLEAN NOT NULL DEFAULT FALSE;
