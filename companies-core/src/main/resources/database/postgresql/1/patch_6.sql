CREATE TABLE IF NOT EXISTS companies_schema.company_inbox
(
    id        BIGSERIAL
        CONSTRAINT company_inbox_pk
            PRIMARY KEY,
    user_uuid bytea                 NOT NULL,
    type      INTEGER               NOT NULL,
    args      jsonb                 NOT NULL,
    created   TIMESTAMP             NOT NULL,
    delivered BOOLEAN DEFAULT FALSE NOT NULL,
    read_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS company_inbox_user_created_index
    ON companies_schema.company_inbox (user_uuid, created);

CREATE INDEX IF NOT EXISTS company_inbox_undelivered_index
    ON companies_schema.company_inbox (user_uuid)
    WHERE NOT delivered;

CREATE INDEX IF NOT EXISTS company_inbox_created_index
    ON companies_schema.company_inbox (created);

INSERT INTO companies_schema.company_inbox(user_uuid, type, args, created, delivered, read_at)
SELECT user_uuid, 0, notification_data, created, FALSE, NULL
FROM companies_schema.company_notification
ORDER BY created;

DROP TABLE IF EXISTS companies_schema.company_notification;

CREATE TABLE IF NOT EXISTS companies_schema.notification_settings
(
    user_uuid bytea
        CONSTRAINT notification_settings_pk
            PRIMARY KEY,
    muted     BIGINT DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS companies_schema.order_payouts
(
    id          BIGSERIAL
        CONSTRAINT order_payouts_pk
            PRIMARY KEY,
    order_id    INTEGER          NOT NULL,
    company_id  INTEGER          NOT NULL
        CONSTRAINT order_payouts_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    worker_uuid bytea            NOT NULL,
    items       INTEGER          NOT NULL,
    gross       DOUBLE PRECISION NOT NULL,
    tax         DOUBLE PRECISION NOT NULL,
    net         DOUBLE PRECISION NOT NULL,
    paid        BOOLEAN          NOT NULL,
    created     TIMESTAMP        NOT NULL
);

CREATE INDEX IF NOT EXISTS order_payouts_company_created_index
    ON companies_schema.order_payouts (company_id, created);

CREATE INDEX IF NOT EXISTS order_payouts_company_worker_index
    ON companies_schema.order_payouts (company_id, worker_uuid);

CREATE INDEX IF NOT EXISTS order_payouts_created_index
    ON companies_schema.order_payouts (created);

INSERT INTO companies_schema.order_payouts(order_id, company_id, worker_uuid, items, gross, tax, net, paid, created)
SELECT d.order_id,
       s.company,
       d.worker_uuid,
       SUM(d.amount),
       SUM(d.amount * c.price::DOUBLE PRECISION / c.amount),
       0,
       SUM(d.amount * c.price::DOUBLE PRECISION / c.amount),
       TRUE,
       s.last_update
FROM companies_schema.order_deliveries d
         JOIN companies_schema.order_content c
              ON c.id = d.order_id
                  AND c.line = d.line
         JOIN companies_schema.order_states s
              ON s.id = d.order_id
WHERE s.state >= 200
  AND s.company IS NOT NULL
  AND EXISTS (SELECT 1 FROM companies_schema.companies co WHERE co.id = s.company)
GROUP BY d.order_id, s.company, d.worker_uuid, s.last_update
ORDER BY d.order_id;

CREATE TABLE IF NOT EXISTS companies_schema.order_history
(
    id         BIGSERIAL
        CONSTRAINT order_history_pk
            PRIMARY KEY,
    order_id   INTEGER   NOT NULL,
    owner_uuid bytea,
    company_id INTEGER,
    outcome    INTEGER   NOT NULL,
    order_name TEXT      NOT NULL,
    placed     TIMESTAMP NOT NULL,
    finished   TIMESTAMP NOT NULL,
    snapshot   jsonb     NOT NULL,
    CONSTRAINT order_history_side
        CHECK ((owner_uuid IS NULL) <> (company_id IS NULL))
);

CREATE INDEX IF NOT EXISTS order_history_owner_index
    ON companies_schema.order_history (owner_uuid, finished);

CREATE INDEX IF NOT EXISTS order_history_company_index
    ON companies_schema.order_history (company_id, finished);

CREATE INDEX IF NOT EXISTS order_history_finished_index
    ON companies_schema.order_history (finished);

CREATE INDEX IF NOT EXISTS order_history_order_index
    ON companies_schema.order_history (order_id);

CREATE OR REPLACE VIEW companies_schema.company_stats_view AS
SELECT c.id,
       c.name,
       c.founded,
       m.member_count,
       o.order_count - COALESCE(s.failed_orders, 0) AS order_count,
       o.price,
       o.amount,
       c.level,
       c.tag,
       c.color
FROM companies_schema.companies c
         LEFT JOIN (SELECT company,
                           COUNT(1)    AS order_count,
                           SUM(price)  AS price,
                           SUM(amount) AS amount
                    FROM companies_schema.orders o
                             LEFT JOIN companies_schema.order_states os ON o.id = os.id
                             LEFT JOIN (SELECT id,
                                               SUM(amount) AS amount,
                                               SUM(price)  AS price
                                        FROM companies_schema.order_content
                                        GROUP BY id) oc
                                       ON o.id = oc.id
                    WHERE os.state >= 200
                    GROUP BY company) o ON c.id = o.company
         LEFT JOIN companies_schema.company_stats s ON c.id = s.id
         LEFT JOIN (SELECT id,
                           COUNT(1) AS member_count
                    FROM companies_schema.company_member
                    GROUP BY id) m ON c.id = m.id;
