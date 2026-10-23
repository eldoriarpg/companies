CREATE TABLE company_treasury
(
    company_id   INTEGER           NOT NULL
        CONSTRAINT company_treasury_pk
            PRIMARY KEY
        CONSTRAINT company_treasury_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    balance      INTEGER DEFAULT 0 NOT NULL
        CONSTRAINT company_treasury_balance_check
            CHECK (balance >= 0),
    payout_share INTEGER DEFAULT 0 NOT NULL
);

CREATE TABLE treasury_log
(
    id         INTEGER  NOT NULL
        CONSTRAINT treasury_log_pk
            PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER  NOT NULL
        CONSTRAINT treasury_log_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    kind       INTEGER  NOT NULL,
    amount     INTEGER  NOT NULL,
    balance    INTEGER  NOT NULL,
    actor_uuid blob,
    order_id   INTEGER,
    note       TEXT,
    created    datetime NOT NULL
);

CREATE INDEX treasury_log_company_created_index
    ON treasury_log (company_id, created);

CREATE INDEX treasury_log_created_index
    ON treasury_log (created);

CREATE TABLE order_series
(
    id            INTEGER           NOT NULL
        CONSTRAINT order_series_pk
            PRIMARY KEY AUTOINCREMENT,
    owner_uuid    blob              NOT NULL,
    name          TEXT              NOT NULL,
    content       TEXT              NOT NULL,
    repeat_every  INTEGER           NOT NULL,
    max_copies    INTEGER,
    ends          datetime,
    posted        INTEGER DEFAULT 0 NOT NULL,
    next_due      datetime          NOT NULL,
    current_order INTEGER,
    state         INTEGER DEFAULT 0 NOT NULL,
    stop_reason   INTEGER,
    waiting       INTEGER,
    order_limit   INTEGER           NOT NULL,
    created       datetime          NOT NULL,
    updated       datetime          NOT NULL
);

CREATE INDEX order_series_due_index
    ON order_series (state, next_due);

CREATE INDEX order_series_owner_index
    ON order_series (owner_uuid);

ALTER TABLE orders
    ADD COLUMN series_id INTEGER;

CREATE INDEX orders_series_index
    ON orders (series_id);

CREATE TABLE order_alerts
(
    id            INTEGER           NOT NULL
        CONSTRAINT order_alerts_pk
            PRIMARY KEY AUTOINCREMENT,
    company_id    INTEGER           NOT NULL
        CONSTRAINT order_alerts_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    name          TEXT              NOT NULL,
    filter        TEXT              NOT NULL,
    creator_uuid  blob              NOT NULL,
    enabled       INTEGER DEFAULT 1 NOT NULL,
    created       datetime          NOT NULL,
    last_notified datetime,
    pending       INTEGER DEFAULT 0 NOT NULL,
    CONSTRAINT order_alerts_uindex
        UNIQUE (company_id, name)
);

CREATE INDEX order_alerts_enabled_index
    ON order_alerts (enabled);

ALTER TABLE order_payouts
    ADD COLUMN share REAL DEFAULT 0 NOT NULL;
