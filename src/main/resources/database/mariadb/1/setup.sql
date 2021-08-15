CREATE OR REPLACE TABLE companies
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

CREATE OR REPLACE TABLE companies_db_version
(
    major INT NULL,
    patch INT NULL
);

CREATE OR REPLACE TABLE company_member
(
    id          INT              NULL,
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
    state       INT                                   NULL,
    CONSTRAINT order_states_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);

CREATE OR REPLACE TABLE orders_delivered
(
    id          INT        NOT NULL,
    worker_uuid BINARY(16) NOT NULL,
    material    TEXT       NOT NULL,
    delivered   INT        NOT NULL,
    CONSTRAINT orders_delivered_id_worker_uuid_material_uindex
        UNIQUE (id, worker_uuid, material) USING HASH,
    CONSTRAINT orders_delivered_orders_id_fk
        FOREIGN KEY (id) REFERENCES orders (id)
            ON DELETE CASCADE
);

CREATE OR REPLACE INDEX orders_delivered_id_index
    ON orders_delivered (id);
