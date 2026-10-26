CREATE TABLE IF NOT EXISTS system_order_decisions
(
    order_id   INT          NOT NULL,
    rule       VARCHAR(64)  NOT NULL,
    kind       VARCHAR(16)  NOT NULL,
    decided_at DATETIME     NOT NULL,
    account    VARCHAR(255) NULL,
    paid       DOUBLE       NOT NULL,
    record     LONGTEXT     NOT NULL,
    CONSTRAINT system_order_decisions_pk
        PRIMARY KEY (order_id)
);

CREATE INDEX IF NOT EXISTS system_order_decisions_rule_index
    ON system_order_decisions (rule, decided_at);

CREATE INDEX IF NOT EXISTS system_order_decisions_decided_index
    ON system_order_decisions (decided_at);
