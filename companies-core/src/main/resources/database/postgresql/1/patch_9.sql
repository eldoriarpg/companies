ALTER TABLE companies_schema.order_states
    ADD COLUMN IF NOT EXISTS solo_worker bytea;

CREATE INDEX IF NOT EXISTS order_states_solo_worker_index
    ON companies_schema.order_states (solo_worker);

CREATE TABLE IF NOT EXISTS companies_schema.solo_cooldowns
(
    worker_uuid bytea     NOT NULL
        CONSTRAINT solo_cooldowns_pk
            PRIMARY KEY,
    ends_at     TIMESTAMP NOT NULL
);

ALTER TABLE companies_schema.order_payouts
    ALTER COLUMN company_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS order_payouts_worker_index
    ON companies_schema.order_payouts (worker_uuid);
