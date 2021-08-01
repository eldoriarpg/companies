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

CREATE OR REPLACE TABLE company_member
(
    id         INT                  NULL,
    uuid       BINARY(16)           NOT NULL
    PRIMARY KEY,
    permission MEDIUMTEXT DEFAULT 0 NOT NULL,
    CONSTRAINT company_member_companies_id_fk
    FOREIGN KEY (id) REFERENCES companies (id)
    ON DELETE CASCADE
);

CREATE OR REPLACE INDEX company_member_id_index
    ON company_member (id);

CREATE OR REPLACE INDEX company_member_id_uuid_index
    ON company_member (id, uuid);

CREATE OR REPLACE TABLE orders
(
    id         BIGINT AUTO_INCREMENT
    PRIMARY KEY,
    owner_uuid BINARY(16)                            NOT NULL,
    name       INT                                   NULL,
    created    TIMESTAMP DEFAULT CURRENT_TIMESTAMP() NOT NULL
);

CREATE OR REPLACE TABLE order_content
(
    id       BIGINT NOT NULL,
    material TEXT   NULL,
    stack    TEXT   NULL,
    amount   INT    NULL,
    price    FLOAT  NULL,
    CONSTRAINT order_content_orders_id_fk
    FOREIGN KEY (id) REFERENCES orders (id)
    ON DELETE CASCADE
);

CREATE OR REPLACE TABLE order_states
(
    id      BIGINT    NOT NULL,
    company INT       NULL,
    claimed TIMESTAMP NULL,
    state   INT       NULL,
    CONSTRAINT order_states_orders_id_fk
    FOREIGN KEY (id) REFERENCES orders (id)
    ON DELETE CASCADE
);

CREATE OR REPLACE TABLE orders_delivered
(
    id          BIGINT     NOT NULL,
    worker_uuid BINARY(16) NOT NULL,
    material    TEXT       NOT NULL,
    delivered   INT        NULL,
    CONSTRAINT orders_delivered_id_worker_uuid_material_uindex
    UNIQUE (id, worker_uuid, material) USING HASH,
    CONSTRAINT orders_delivered_orders_id_fk
    FOREIGN KEY (id) REFERENCES orders (id)
    ON DELETE CASCADE
);

CREATE OR REPLACE INDEX orders_delivered_id_index
    ON orders_delivered (id);

CREATE OR REPLACE INDEX orders_delivered_id_worker_uuid_index
    ON orders_delivered (id, worker_uuid);
