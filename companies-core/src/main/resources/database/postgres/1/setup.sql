CREATE TABLE IF NOT EXISTS orders
(
    id         SERIAL
        CONSTRAINT orders_pk
            PRIMARY KEY,
    owner_uuid bytea,
    name       TEXT                    NOT NULL,
    created    TIMESTAMP DEFAULT NOW() NOT NULL
);

CREATE INDEX IF NOT EXISTS orders_owner_uuid_index
    ON orders (owner_uuid);

CREATE TABLE IF NOT EXISTS order_states
(
    id          INTEGER                 NOT NULL
        CONSTRAINT order_states_pk
            PRIMARY KEY
        CONSTRAINT order_states_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    company     INTEGER,
    last_update TIMESTAMP DEFAULT NOW() NOT NULL,
    state       INTEGER   DEFAULT 0     NOT NULL
);

CREATE INDEX IF NOT EXISTS order_states_company_index
    ON order_states (company);

CREATE TABLE IF NOT EXISTS order_content
(
    id       INTEGER NOT NULL
        CONSTRAINT order_content_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    material TEXT    NOT NULL,
    stack    TEXT    NOT NULL,
    amount   INTEGER NOT NULL,
    price    REAL    NOT NULL,
    CONSTRAINT order_content_pk
        UNIQUE (id, material, stack)
);

CREATE INDEX IF NOT EXISTS order_content_id_index
    ON order_content (id);

CREATE TABLE IF NOT EXISTS orders_delivered
(
    id          INTEGER NOT NULL
        CONSTRAINT orders_delivered_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    worker_uuid bytea   NOT NULL,
    material    TEXT    NOT NULL,
    delivered   INTEGER NOT NULL,
    CONSTRAINT orders_delivered_pk
        UNIQUE (id, worker_uuid, material)
);

CREATE INDEX IF NOT EXISTS orders_delivered_id_index
    ON orders_delivered (id);

CREATE TABLE IF NOT EXISTS companies
(
    id      SERIAL
        CONSTRAINT companies_pk
            PRIMARY KEY,
    name    TEXT                    NOT NULL,
    founded TIMESTAMP DEFAULT NOW() NOT NULL,
    level   INT       DEFAULT 1     NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS companies_name_uindex
    ON companies (name);

CREATE TABLE IF NOT EXISTS company_member
(
    id          INTEGER          NOT NULL
        CONSTRAINT company_member_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    member_uuid bytea            NOT NULL
        CONSTRAINT company_member_pk
            PRIMARY KEY,
    permission  BIGINT DEFAULT 0 NOT NULL
);

CREATE INDEX IF NOT EXISTS company_member_id_index
    ON company_member (id);

CREATE INDEX IF NOT EXISTS company_member_id_uuid_index
    ON company_member (id, member_uuid);

CREATE TABLE IF NOT EXISTS company_notification
(
    user_uuid         bytea                   NOT NULL,
    created           TIMESTAMP DEFAULT NOW() NOT NULL,
    notification_data jsonb                   NOT NULL
);

CREATE INDEX IF NOT EXISTS notification_user_uuid_index
    ON company_notification (user_uuid);

CREATE TABLE IF NOT EXISTS company_stats
(
    id            INTEGER           NOT NULL
        CONSTRAINT company_stats_pk
            PRIMARY KEY
        CONSTRAINT company_stats_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    failed_orders INTEGER DEFAULT 0 NOT NULL
);

CREATE OR REPLACE VIEW company_stats_view AS
SELECT c.id,
       c.name,
       c.founded,
       m.member_count,
       o.order_count - coalesce(s.failed_orders, 0) AS order_count,
       o.price,
       o.amount
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
                    WHERE os.state >= 300
                    GROUP BY company) o ON c.id = o.company
         LEFT JOIN company_stats s ON c.id = s.id
         LEFT JOIN (SELECT id,
                           COUNT(1) AS member_count
                    FROM company_member
                    GROUP BY id) m ON c.id = m.id;

CREATE MATERIALIZED VIEW material_price AS
SELECT material, avg_price, min_price, max_price
FROM (SELECT c.material,
             AVG(c.price / c.amount) AS avg_price,
             MIN(c.price / c.amount) AS min_price,
             MAX(c.price / c.amount) AS max_price
      FROM (SELECT ROW_NUMBER() OVER (PARTITION BY material ORDER BY last_update DESC) AS id, material, amount, price, last_update
            FROM order_content c
                     LEFT JOIN order_states s ON c.id = s.id
            WHERE s.state >= 200) c
      WHERE c.id < 100
      GROUP BY c.material) avg;
