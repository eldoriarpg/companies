CREATE TABLE companies
(
    id      INT AUTO_INCREMENT
        CONSTRAINT companies_pk
            PRIMARY KEY
        CONSTRAINT companies_id_uindex
            UNIQUE,
    name    TEXT NOT NULL
        CONSTRAINT companies_name_uindex
            UNIQUE,
    founded TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE company_member
(
    id         INTEGER NOT NULL ,
    uuid       BLOB NOT NULL
        CONSTRAINT company_member_pk
            PRIMARY KEY,
    permission TEXT
);

CREATE INDEX company_member_id_index
    ON company_member (id);

CREATE INDEX company_member_id_uuid_index
    ON company_member (id, uuid);

CREATE TABLE orders
(
    id         INTEGER
        CONSTRAINT orders_pk
            PRIMARY KEY,
    owner_uuid BLOB NOT NULL ,
    name       TEXT NOT NULL ,
    created    DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE order_content
(
    id       INTEGER
        CONSTRAINT order_content_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    material TEXT NOT NULL ,
    stack    TEXT NOT NULL ,
    amount   INTEGER NOT NULL ,
    price    FLOAT NOT NULL
);

CREATE INDEX order_content_id_index
    ON order_content (id);

CREATE TABLE order_states
(
    id          INTEGER
        CONSTRAINT order_states_pk
            PRIMARY KEY
        CONSTRAINT order_states_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    company     INTEGER,
    last_update DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    state       INTEGER
);

CREATE TABLE orders_delivered
(
    id          INTEGER
        CONSTRAINT orders_delivered_orders_id_fk
            REFERENCES orders
            ON DELETE CASCADE,
    worker_uuid BLOB,
    material    TEXT,
    delivered   INTEGER,
    CONSTRAINT orders_delivered_pk
        PRIMARY KEY (id, worker_uuid, material)
);

CREATE INDEX orders_delivered_id_index
    ON orders_delivered (id);

CREATE INDEX orders_delivered_id_worker_uuid_index
    ON orders_delivered (id, worker_uuid);
