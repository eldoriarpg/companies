CREATE TABLE company_inbox
(
    id        INTEGER           NOT NULL
        CONSTRAINT company_inbox_pk
            PRIMARY KEY AUTOINCREMENT,
    user_uuid blob              NOT NULL,
    type      INTEGER           NOT NULL,
    args      TEXT              NOT NULL,
    created   datetime          NOT NULL,
    delivered INTEGER DEFAULT 0 NOT NULL,
    read_at   datetime
);

CREATE INDEX company_inbox_user_created_index
    ON company_inbox (user_uuid, created);

CREATE INDEX company_inbox_user_delivered_index
    ON company_inbox (user_uuid, delivered);

CREATE INDEX company_inbox_created_index
    ON company_inbox (created);

INSERT INTO company_inbox(user_uuid, type, args, created, delivered, read_at)
SELECT user_uuid, 0, notification_data, created, 0, NULL
FROM company_notification
ORDER BY created, ROWID;

DROP TABLE company_notification;

CREATE TABLE notification_settings
(
    user_uuid blob              NOT NULL
        CONSTRAINT notification_settings_pk
            PRIMARY KEY,
    muted     INTEGER DEFAULT 0 NOT NULL
);

CREATE TABLE order_payouts
(
    id          INTEGER  NOT NULL
        CONSTRAINT order_payouts_pk
            PRIMARY KEY AUTOINCREMENT,
    order_id    INTEGER  NOT NULL,
    company_id  INTEGER  NOT NULL
        CONSTRAINT order_payouts_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    worker_uuid blob     NOT NULL,
    items       INTEGER  NOT NULL,
    gross       REAL     NOT NULL,
    tax         REAL     NOT NULL,
    net         REAL     NOT NULL,
    paid        INTEGER  NOT NULL,
    created     datetime NOT NULL
);

CREATE INDEX order_payouts_company_created_index
    ON order_payouts (company_id, created);

CREATE INDEX order_payouts_company_worker_index
    ON order_payouts (company_id, worker_uuid);

CREATE INDEX order_payouts_created_index
    ON order_payouts (created);

INSERT INTO order_payouts(order_id, company_id, worker_uuid, items, gross, tax, net, paid, created)
SELECT d.order_id,
       s.company,
       d.worker_uuid,
       SUM(d.amount),
       SUM(d.amount * c.price / c.amount),
       0,
       SUM(d.amount * c.price / c.amount),
       1,
       s.last_update
FROM order_deliveries d
         JOIN order_content c
              ON c.id = d.order_id
                  AND c.line = d.line
         JOIN order_states s
              ON s.id = d.order_id
WHERE s.state >= 200
  AND s.company IS NOT NULL
  AND EXISTS (SELECT 1 FROM companies co WHERE co.id = s.company)
GROUP BY d.order_id, s.company, d.worker_uuid, s.last_update
ORDER BY d.order_id;

CREATE TABLE order_history
(
    id         INTEGER  NOT NULL
        CONSTRAINT order_history_pk
            PRIMARY KEY AUTOINCREMENT,
    order_id   INTEGER  NOT NULL,
    owner_uuid blob,
    company_id INTEGER,
    outcome    INTEGER  NOT NULL,
    order_name TEXT     NOT NULL,
    placed     datetime NOT NULL,
    finished   datetime NOT NULL,
    snapshot   TEXT     NOT NULL,
    CONSTRAINT order_history_side
        CHECK ((owner_uuid IS NULL) <> (company_id IS NULL))
);

CREATE INDEX order_history_owner_index
    ON order_history (owner_uuid, finished);

CREATE INDEX order_history_company_index
    ON order_history (company_id, finished);

CREATE INDEX order_history_finished_index
    ON order_history (finished);

CREATE INDEX order_history_order_index
    ON order_history (order_id);

DROP VIEW IF EXISTS company_stats_view;

CREATE VIEW company_stats_view AS
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
FROM companies c
         LEFT JOIN (SELECT company,
                           COUNT(1)    AS order_count,
                           SUM(price)  AS price,
                           SUM(amount) AS amount
                    FROM orders o
                             LEFT JOIN order_states os ON o.id = os.id
                             LEFT JOIN (SELECT id,
                                               SUM(amount) AS amount,
                                               SUM(price)  AS price
                                        FROM order_content
                                        GROUP BY id) oc
                                       ON o.id = oc.id
                    WHERE os.state >= 200
                    GROUP BY company) o ON c.id = o.company
         LEFT JOIN company_stats s ON c.id = s.id
         LEFT JOIN (SELECT id,
                           COUNT(1) AS member_count
                    FROM company_member
                    GROUP BY id) m ON c.id = m.id;
