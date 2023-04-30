CREATE TABLE companies
(
    id      INTEGER
        CONSTRAINT companies_pk
            PRIMARY KEY AUTOINCREMENT,
    name    TEXT,
    founded datetime DEFAULT CURRENT_TIMESTAMP,
    level   INT      DEFAULT 1 NOT NULL
);

CREATE UNIQUE INDEX companies_name_uindex
    ON companies (name);

CREATE TABLE company_member
(
    id          INTEGER           NOT NULL,
    member_uuid blob              NOT NULL
        CONSTRAINT company_member_pk
            PRIMARY KEY,
    permission  INTEGER DEFAULT 0 NOT NULL
);

CREATE INDEX company_member_id_index
    ON company_member (id);

CREATE INDEX company_member_id_uuid_index
    ON company_member (id, member_uuid);

CREATE TABLE orders
(
    id         INTEGER                            NOT NULL
        CONSTRAINT orders_pk
            PRIMARY KEY AUTOINCREMENT,
    owner_uuid blob                               NOT NULL,
    name       INTEGER                            NOT NULL,
    created    datetime DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX orders_owner_uuid_index
    ON orders (owner_uuid);

CREATE TABLE order_content
(
    id       INTEGER NOT NULL
        CONSTRAINT order_content_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    material TEXT    NOT NULL,
    stack    TEXT    NOT NULL,
    amount   INTEGER NOT NULL,
    price    FLOAT   NOT NULL
);

CREATE INDEX order_content_id_index
    ON order_content (id);

CREATE TABLE order_states
(
    id          INTEGER                            NOT NULL
        CONSTRAINT order_states_pk
            PRIMARY KEY
        CONSTRAINT order_states_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    company     INTEGER,
    last_update datetime DEFAULT CURRENT_TIMESTAMP NOT NULL,
    state       INTEGER  DEFAULT 0                 NOT NULL
);

CREATE TABLE orders_delivered
(
    id          INTEGER NOT NULL
        CONSTRAINT orders_delivered_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    worker_uuid blob    NOT NULL,
    material    TEXT    NOT NULL,
    delivered   INTEGER NOT NULL,
    CONSTRAINT orders_delivered_pk
        PRIMARY KEY (id, worker_uuid, material)
);

CREATE INDEX orders_delivered_id_index
    ON orders_delivered (id);

CREATE INDEX orders_delivered_id_worker_uuid_index
    ON orders_delivered (id, worker_uuid);

CREATE TABLE company_notification
(
    user_uuid         blob                                NOT NULL,
    created           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    notification_data TEXT                                NOT NULL
);

CREATE INDEX notification_user_uuid_index
    ON company_notification (user_uuid);

CREATE TABLE company_stats
(
    id            INT NOT NULL
        CONSTRAINT company_stats_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    failed_orders INT DEFAULT 0
);


CREATE UNIQUE INDEX company_stats_id_uindex
    ON company_stats (id);

DROP VIEW IF EXISTS company_stats_view;
CREATE VIEW company_stats_view AS
SELECT c.id,
       c.name,
       c.founded,
       m.member_count,
       o.order_count - COALESCE(s.failed_orders, 0) AS order_count,
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
                    WHERE os.state >= 200
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

create table node
(
    id      INTEGER PRIMARY KEY AUTOINCREMENT                   not null,
    uid     blob                  not null,
    type    text default 'PRIMARY' not null,
    version text                   not null,
    constraint node_id_uindex
        unique (uid)
);

create table node_configuration
(
    node_id int not null,
    path    text  not null,
    content text  not null,
    constraint node_configuration_node_id_fk
        foreign key (node_id) references node (id)
            on delete cascade

);

create unique index node_configuration_node_id_path_uindex
    on node_configuration (node_id, path);
