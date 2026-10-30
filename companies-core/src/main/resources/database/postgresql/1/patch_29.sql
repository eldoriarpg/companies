ALTER TABLE companies_schema.order_payouts
    ADD COLUMN IF NOT EXISTS event_extra DOUBLE PRECISION NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS companies_schema.payout_events
(
    payout_id BIGINT           NOT NULL
        CONSTRAINT payout_events_payout_fk
            REFERENCES companies_schema.order_payouts
            ON DELETE CASCADE,
    event_id  INTEGER          NOT NULL
        CONSTRAINT payout_events_event_fk
            REFERENCES companies_schema.server_events
            ON DELETE CASCADE,
    amount    DOUBLE PRECISION NOT NULL,
    CONSTRAINT payout_events_pk
        PRIMARY KEY (payout_id, event_id)
);

CREATE INDEX IF NOT EXISTS payout_events_event_index
    ON companies_schema.payout_events (event_id);
