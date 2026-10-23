CREATE TABLE IF NOT EXISTS company_treasury
(
    company_id   INT              NOT NULL
        PRIMARY KEY,
    balance      BIGINT DEFAULT 0 NOT NULL,
    payout_share INT    DEFAULT 0 NOT NULL,
    CONSTRAINT company_treasury_balance_check
        CHECK (balance >= 0),
    CONSTRAINT company_treasury_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS treasury_log
(
    id         BIGINT AUTO_INCREMENT
        PRIMARY KEY,
    company_id INT        NOT NULL,
    kind       INT        NOT NULL,
    amount     BIGINT     NOT NULL,
    balance    BIGINT     NOT NULL,
    actor_uuid BINARY(16) NULL,
    order_id   INT        NULL,
    note       TEXT       NULL,
    created    DATETIME   NOT NULL,
    CONSTRAINT treasury_log_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS treasury_log_company_created_index
    ON treasury_log (company_id, created);

CREATE INDEX IF NOT EXISTS treasury_log_created_index
    ON treasury_log (created);

CREATE TABLE IF NOT EXISTS order_series
(
    id            INT AUTO_INCREMENT
        PRIMARY KEY,
    owner_uuid    BINARY(16)    NOT NULL,
    name          TEXT          NOT NULL,
    content       LONGTEXT      NOT NULL,
    repeat_every  INT           NOT NULL,
    max_copies    INT           NULL,
    ends          DATETIME      NULL,
    posted        INT DEFAULT 0 NOT NULL,
    next_due      DATETIME      NOT NULL,
    current_order INT           NULL,
    state         INT DEFAULT 0 NOT NULL,
    stop_reason   INT           NULL,
    waiting       INT           NULL,
    order_limit   INT           NOT NULL,
    created       DATETIME      NOT NULL,
    updated       DATETIME      NOT NULL
);

CREATE INDEX IF NOT EXISTS order_series_due_index
    ON order_series (state, next_due);

CREATE INDEX IF NOT EXISTS order_series_owner_index
    ON order_series (owner_uuid);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS series_id INT NULL;

CREATE INDEX IF NOT EXISTS orders_series_index
    ON orders (series_id);

CREATE TABLE IF NOT EXISTS order_alerts
(
    id            INT AUTO_INCREMENT
        PRIMARY KEY,
    company_id    INT                  NOT NULL,
    name          VARCHAR(32)          NOT NULL,
    filter        TEXT                 NOT NULL,
    creator_uuid  BINARY(16)           NOT NULL,
    enabled       BOOLEAN DEFAULT TRUE NOT NULL,
    created       DATETIME             NOT NULL,
    last_notified DATETIME             NULL,
    pending       INT     DEFAULT 0    NOT NULL,
    CONSTRAINT order_alerts_uindex
        UNIQUE (company_id, name),
    CONSTRAINT order_alerts_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS order_alerts_enabled_index
    ON order_alerts (enabled);

ALTER TABLE order_payouts
    ADD COLUMN IF NOT EXISTS share DOUBLE DEFAULT 0 NOT NULL;
