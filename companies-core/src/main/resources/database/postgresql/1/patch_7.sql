CREATE TABLE IF NOT EXISTS companies_schema.company_treasury
(
    company_id   INTEGER
        CONSTRAINT company_treasury_pk
            PRIMARY KEY
        CONSTRAINT company_treasury_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    balance      BIGINT  DEFAULT 0 NOT NULL
        CONSTRAINT company_treasury_balance_check
            CHECK (balance >= 0),
    payout_share INTEGER DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS companies_schema.treasury_log
(
    id         BIGSERIAL
        CONSTRAINT treasury_log_pk
            PRIMARY KEY,
    company_id INTEGER   NOT NULL
        CONSTRAINT treasury_log_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    kind       INTEGER   NOT NULL,
    amount     BIGINT    NOT NULL,
    balance    BIGINT    NOT NULL,
    actor_uuid bytea,
    order_id   INTEGER,
    note       TEXT,
    created    TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS treasury_log_company_created_index
    ON companies_schema.treasury_log (company_id, created);

CREATE INDEX IF NOT EXISTS treasury_log_created_index
    ON companies_schema.treasury_log (created);

CREATE TABLE IF NOT EXISTS companies_schema.order_series
(
    id            SERIAL
        CONSTRAINT order_series_pk
            PRIMARY KEY,
    owner_uuid    bytea             NOT NULL,
    name          TEXT              NOT NULL,
    content       TEXT              NOT NULL,
    repeat_every  INTEGER           NOT NULL,
    max_copies    INTEGER,
    ends          TIMESTAMP,
    posted        INTEGER DEFAULT 0 NOT NULL,
    next_due      TIMESTAMP         NOT NULL,
    current_order INTEGER,
    state         INTEGER DEFAULT 0 NOT NULL,
    stop_reason   INTEGER,
    waiting       INTEGER,
    order_limit   INTEGER           NOT NULL,
    created       TIMESTAMP         NOT NULL,
    updated       TIMESTAMP         NOT NULL
);

CREATE INDEX IF NOT EXISTS order_series_due_index
    ON companies_schema.order_series (state, next_due);

CREATE INDEX IF NOT EXISTS order_series_owner_index
    ON companies_schema.order_series (owner_uuid);

ALTER TABLE companies_schema.orders
    ADD COLUMN IF NOT EXISTS series_id INTEGER;

CREATE INDEX IF NOT EXISTS orders_series_index
    ON companies_schema.orders (series_id);

CREATE TABLE IF NOT EXISTS companies_schema.order_alerts
(
    id            SERIAL
        CONSTRAINT order_alerts_pk
            PRIMARY KEY,
    company_id    INTEGER                NOT NULL
        CONSTRAINT order_alerts_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    name          TEXT                   NOT NULL,
    filter        TEXT                   NOT NULL,
    creator_uuid  bytea                  NOT NULL,
    enabled       BOOLEAN DEFAULT TRUE   NOT NULL,
    created       TIMESTAMP              NOT NULL,
    last_notified TIMESTAMP,
    pending       INTEGER DEFAULT 0      NOT NULL,
    CONSTRAINT order_alerts_uindex
        UNIQUE (company_id, name)
);

CREATE INDEX IF NOT EXISTS order_alerts_enabled_index
    ON companies_schema.order_alerts (enabled);

ALTER TABLE companies_schema.order_payouts
    ADD COLUMN IF NOT EXISTS share DOUBLE PRECISION DEFAULT 0 NOT NULL;
