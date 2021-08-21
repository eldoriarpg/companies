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
    founded TIMESTAMP DEFAULT NOW() NOT NULL
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

create table companies.notification
(
    user_uuid bytea not null
        constraint notification_pk
            primary key,
    created timestamp default now() not null,
    notification_data text not null
);
