ALTER TABLE order_states
    ADD COLUMN IF NOT EXISTS solo_worker BINARY(16) NULL;

CREATE INDEX IF NOT EXISTS order_states_solo_worker_index
    ON order_states (solo_worker);

CREATE TABLE IF NOT EXISTS solo_cooldowns
(
    worker_uuid BINARY(16) NOT NULL
        PRIMARY KEY,
    ends_at     DATETIME   NOT NULL
);

ALTER TABLE order_payouts
    MODIFY company_id INT NULL;

CREATE INDEX IF NOT EXISTS order_payouts_worker_index
    ON order_payouts (worker_uuid);
