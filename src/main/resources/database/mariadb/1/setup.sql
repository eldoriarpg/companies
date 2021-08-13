CREATE TABLE companies
(
    id      INT AUTO_INCREMENT,
    name    TEXT                                  NOT NULL,
    founded TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL,
    CONSTRAINT companies_id_uindex
        UNIQUE (id),
    CONSTRAINT companies_name_uindex
        UNIQUE (name) USING HASH
);

ALTER TABLE companies
    ADD PRIMARY KEY (id);

CREATE TABLE company_member
(
    id         INT                  NULL,
    uuid       BINARY(16)           NOT NULL
        PRIMARY KEY,
    permission MEDIUMTEXT DEFAULT 0 NOT NULL,
    CONSTRAINT company_member_companies_id_fk
        FOREIGN KEY (id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX company_member_id_index
    ON company_member (id);

CREATE INDEX company_member_id_uuid_index
    ON company_member (id, uuid);

CREATE TABLE orders
(
    id         INTEGER AUTO_INCREMENT
        PRIMARY KEY,
    owner_uuid BINARY(16)                            NOT NULL,
    name       INT                                   NULL,
    created    TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL
);

CREATE TABLE order_content
(
    id       INTEGER NOT NULL,
    material TEXT    NULL,
    stack    TEXT    NULL,
    amount   INT     NULL,
    price    FLOAT   NULL,
    CONSTRAINT order_content_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);

CREATE TABLE order_states
(
    id          INTEGER                               NOT NULL
        PRIMARY KEY,
    company     INT                                   NULL,
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL,
    state       INT                                   NULL,
    CONSTRAINT order_states_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);

create table orders_delivered
(
    id int not null,
    worker_uuid BINARY(16) not null,
    material TEXT not null,
    delivered int not null,
    constraint orders_delivered_orders_id_fk
        foreign key (id) references orders (id)
            on delete cascade
);

create index orders_delivered_id_index
    on orders_delivered (id);

create unique index orders_delivered_id_worker_uuid_material_uindex
    on orders_delivered (id, worker_uuid, material);
