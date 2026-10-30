ALTER TABLE order_payouts
    ADD COLUMN IF NOT EXISTS event_extra DOUBLE NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS payout_events
(
    payout_id BIGINT NOT NULL,
    event_id  INT    NOT NULL,
    amount    DOUBLE NOT NULL,
    CONSTRAINT payout_events_pk
        PRIMARY KEY (payout_id, event_id),
    CONSTRAINT payout_events_payout_fk
        FOREIGN KEY (payout_id) REFERENCES order_payouts (id)
            ON DELETE CASCADE,
    CONSTRAINT payout_events_event_fk
        FOREIGN KEY (event_id) REFERENCES server_events (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS payout_events_event_index
    ON payout_events (event_id);
