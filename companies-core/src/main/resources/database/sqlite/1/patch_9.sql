ALTER TABLE order_states
    ADD COLUMN solo_worker blob;

CREATE INDEX order_states_solo_worker_index
    ON order_states (solo_worker);

CREATE TABLE solo_cooldowns
(
    worker_uuid blob     NOT NULL
        CONSTRAINT solo_cooldowns_pk
            PRIMARY KEY,
    ends_at     datetime NOT NULL
);

ALTER TABLE order_payouts
    RENAME TO order_payouts_company_only;

CREATE TABLE order_payouts
(
    id          INTEGER  NOT NULL
        CONSTRAINT order_payouts_pk
            PRIMARY KEY AUTOINCREMENT,
    order_id    INTEGER  NOT NULL,
    company_id  INTEGER
        CONSTRAINT order_payouts_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    worker_uuid blob     NOT NULL,
    items       INTEGER  NOT NULL,
    gross       REAL     NOT NULL,
    tax         REAL     NOT NULL,
    net         REAL     NOT NULL,
    paid        INTEGER  NOT NULL,
    created     datetime NOT NULL,
    share       REAL DEFAULT 0 NOT NULL
);

INSERT INTO order_payouts(id, order_id, company_id, worker_uuid, items, gross, tax, net, paid, created, share)
SELECT id, order_id, company_id, worker_uuid, items, gross, tax, net, paid, created, share
FROM order_payouts_company_only;

DROP TABLE order_payouts_company_only;

CREATE INDEX order_payouts_company_created_index
    ON order_payouts (company_id, created);

CREATE INDEX order_payouts_company_worker_index
    ON order_payouts (company_id, worker_uuid);

CREATE INDEX order_payouts_created_index
    ON order_payouts (created);

CREATE INDEX order_payouts_worker_index
    ON order_payouts (worker_uuid);
