CREATE OR REPLACE TABLE companies
(
    id      INT AUTO_INCREMENT,
    name    TEXT                                  NOT NULL,
    founded TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL,
    level   INT       DEFAULT 1                   NOT NULL,
    CONSTRAINT companies_id_uindex
        UNIQUE (id),
    CONSTRAINT companies_name_uindex
        UNIQUE (name) USING HASH
);

ALTER TABLE companies
    ADD PRIMARY KEY (id);

CREATE OR REPLACE TABLE company_member
(
    id          INT              NOT NULL,
    member_uuid BINARY(16)       NOT NULL
        PRIMARY KEY,
    permission  BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT company_member_companies_id_fk
        FOREIGN KEY (id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE OR REPLACE INDEX company_member_id_index
    ON company_member (id);

CREATE OR REPLACE INDEX company_member_id_uuid_index
    ON company_member (id, member_uuid);

CREATE OR REPLACE TABLE orders
(
    id         INT AUTO_INCREMENT
        PRIMARY KEY,
    owner_uuid BINARY(16)                            NOT NULL,
    name       TEXT                                  NULL,
    created    TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL
);

CREATE OR REPLACE TABLE order_content
(
    id       INT   NOT NULL,
    material TEXT  NOT NULL,
    stack    TEXT  NOT NULL,
    amount   INT   NOT NULL,
    price    FLOAT NOT NULL,
    CONSTRAINT order_content_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);

CREATE OR REPLACE TABLE order_states
(
    id          INT                                   NOT NULL
        PRIMARY KEY,
    company     INT                                   NULL,
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL,
    state       INT       DEFAULT 0                   NOT NULL,
    CONSTRAINT order_states_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);

CREATE OR REPLACE INDEX orders_owner_uuid_index
    ON orders (owner_uuid);

CREATE OR REPLACE TABLE orders_delivered
(
    id          INT        NOT NULL,
    worker_uuid BINARY(16) NOT NULL,
    material    TEXT       NOT NULL,
    delivered   INT        NOT NULL,
    CONSTRAINT orders_delivered_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);

CREATE OR REPLACE INDEX orders_delivered_id_index
    ON orders_delivered (id);

CREATE OR REPLACE TABLE company_notification
(
    user_uuid         BINARY(16)                            NOT NULL,
    created           TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL,
    notification_data TEXT                                  NOT NULL
);

CREATE OR REPLACE INDEX notification_user_uuid_index
    ON company_notification (user_uuid);

CREATE OR REPLACE TABLE company_stats
(
    id            INT           NOT NULL
        PRIMARY KEY,
    failed_orders INT DEFAULT 0 NOT NULL,
    CONSTRAINT company_stats_companies_id_fk
        FOREIGN KEY (id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE OR REPLACE VIEW company_stats_view AS
SELECT c.id,
       c.name,
       c.founded,
       m.member_count,
       o.order_count - s.failed_orders AS order_count,
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

CREATE TABLE material_price
(
    material  VARCHAR(255) NOT NULL,
    avg_price DOUBLE       NOT NULL,
    min_price DOUBLE       NOT NULL,
    max_price DOUBLE       NOT NULL,
    CONSTRAINT material_price_pk
        PRIMARY KEY (material)
);
