CREATE TABLE system_order_decisions
(
    order_id   INTEGER  NOT NULL,
    rule       TEXT     NOT NULL,
    kind       TEXT     NOT NULL,
    decided_at datetime NOT NULL,
    account    TEXT,
    paid       REAL     NOT NULL,
    record     TEXT     NOT NULL,
    CONSTRAINT system_order_decisions_pk
        PRIMARY KEY (order_id)
);

CREATE INDEX system_order_decisions_rule_index
    ON system_order_decisions (rule, decided_at);

CREATE INDEX system_order_decisions_decided_index
    ON system_order_decisions (decided_at);
