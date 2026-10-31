CREATE TABLE IF NOT EXISTS area_offers
(
    id          INT AUTO_INCREMENT NOT NULL,
    provider    VARCHAR(32)        NOT NULL,
    ref         VARCHAR(255)       NOT NULL,
    world       VARCHAR(64)        NOT NULL,
    node        BINARY(16)         NOT NULL,
    kind        VARCHAR(8)         NOT NULL,
    price       DOUBLE             NOT NULL,
    period_days INT                NOT NULL DEFAULT 0,
    keeper      BINARY(16)         NULL,
    created     DATETIME           NOT NULL,
    CONSTRAINT area_offers_pk
        PRIMARY KEY (id),
    CONSTRAINT area_offers_ref_uindex
        UNIQUE (provider, ref)
);

ALTER TABLE company_areas
    ADD COLUMN IF NOT EXISTS offer_id    INT     NULL,
    ADD COLUMN IF NOT EXISTS renew       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS rent_warned BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rent_ended  BOOLEAN NOT NULL DEFAULT FALSE;
