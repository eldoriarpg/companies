create table companies
(
    id INTEGER
        constraint companies_pk
            primary key autoincrement,
    name TEXT,
    founded datetime default current_timestamp
);

create unique index companies_name_uindex
    on companies (name);

CREATE TABLE company_member
(
    id          INTEGER NOT NULL,
    member_uuid BLOB    NOT NULL
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
    id         INTEGER NOT NULL
        CONSTRAINT orders_pk
            PRIMARY KEY AUTOINCREMENT,
    owner_uuid BLOB    NOT NULL,
    name       INTEGER NOT NULL,
    created    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL
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
    id          INTEGER NOT NULL
        CONSTRAINT order_states_pk
            PRIMARY KEY
        CONSTRAINT order_states_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    company     INTEGER,
    last_update DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    state       INTEGER  DEFAULT 0 NOT NULL
);

CREATE TABLE orders_delivered
(
    id          INTEGER NOT NULL
        CONSTRAINT orders_delivered_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    worker_uuid BLOB    NOT NULL,
    material    TEXT    NOT NULL,
    delivered   INTEGER NOT NULL,
    CONSTRAINT orders_delivered_pk
        PRIMARY KEY (id, worker_uuid, material)
);

CREATE INDEX orders_delivered_id_index
    ON orders_delivered (id);

CREATE INDEX orders_delivered_id_worker_uuid_index
    ON orders_delivered (id, worker_uuid);

CREATE TABLE notification
(
    user_uuid         BLOB NOT NULL,
    created           TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    notification_data TEXT NOT NULL
);

CREATE INDEX notification_user_uuid_index
    ON notification (user_uuid);
