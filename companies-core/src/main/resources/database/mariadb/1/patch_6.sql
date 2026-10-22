CREATE TABLE IF NOT EXISTS company_inbox
(
    id        BIGINT AUTO_INCREMENT
        PRIMARY KEY,
    user_uuid BINARY(16)            NOT NULL,
    type      INT                   NOT NULL,
    args      TEXT                  NOT NULL,
    created   DATETIME              NOT NULL,
    delivered BOOLEAN DEFAULT FALSE NOT NULL,
    read_at   DATETIME              NULL
);

CREATE INDEX IF NOT EXISTS company_inbox_user_created_index
    ON company_inbox (user_uuid, created);

CREATE INDEX IF NOT EXISTS company_inbox_user_delivered_index
    ON company_inbox (user_uuid, delivered);

CREATE INDEX IF NOT EXISTS company_inbox_created_index
    ON company_inbox (created);

INSERT INTO company_inbox(user_uuid, type, args, created, delivered, read_at)
SELECT user_uuid, 0, notification_data, created, FALSE, NULL
FROM company_notification
ORDER BY created;

DROP TABLE IF EXISTS company_notification;

CREATE TABLE IF NOT EXISTS notification_settings
(
    user_uuid BINARY(16)       NOT NULL
        PRIMARY KEY,
    muted     BIGINT DEFAULT 0 NOT NULL
);

CREATE TABLE IF NOT EXISTS order_payouts
(
    id          BIGINT AUTO_INCREMENT
        PRIMARY KEY,
    order_id    INT        NOT NULL,
    company_id  INT        NOT NULL,
    worker_uuid BINARY(16) NOT NULL,
    items       INT        NOT NULL,
    gross       DOUBLE     NOT NULL,
    tax         DOUBLE     NOT NULL,
    net         DOUBLE     NOT NULL,
    paid        BOOLEAN    NOT NULL,
    created     DATETIME   NOT NULL,
    CONSTRAINT order_payouts_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS order_payouts_company_created_index
    ON order_payouts (company_id, created);

CREATE INDEX IF NOT EXISTS order_payouts_company_worker_index
    ON order_payouts (company_id, worker_uuid);

CREATE INDEX IF NOT EXISTS order_payouts_created_index
    ON order_payouts (created);

INSERT INTO order_payouts(order_id, company_id, worker_uuid, items, gross, tax, net, paid, created)
SELECT d.order_id,
       s.company,
       d.worker_uuid,
       SUM(d.amount),
       SUM(d.amount * c.price / c.amount),
       0,
       SUM(d.amount * c.price / c.amount),
       TRUE,
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

CREATE TABLE IF NOT EXISTS order_history
(
    id         BIGINT AUTO_INCREMENT
        PRIMARY KEY,
    order_id   INT        NOT NULL,
    owner_uuid BINARY(16) NULL,
    company_id INT        NULL,
    outcome    INT        NOT NULL,
    order_name TEXT       NOT NULL,
    placed     DATETIME   NOT NULL,
    finished   DATETIME   NOT NULL,
    snapshot   LONGTEXT   NOT NULL,
    CONSTRAINT order_history_side
        CHECK ((owner_uuid IS NULL) <> (company_id IS NULL))
);

CREATE INDEX IF NOT EXISTS order_history_owner_index
    ON order_history (owner_uuid, finished);

CREATE INDEX IF NOT EXISTS order_history_company_index
    ON order_history (company_id, finished);

CREATE INDEX IF NOT EXISTS order_history_finished_index
    ON order_history (finished);

CREATE INDEX IF NOT EXISTS order_history_order_index
    ON order_history (order_id);

CREATE OR REPLACE VIEW company_stats_view AS
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
