ALTER TABLE order_payouts
    ADD COLUMN event_extra REAL DEFAULT 0 NOT NULL;

CREATE TABLE payout_events
(
    payout_id INTEGER NOT NULL
        CONSTRAINT payout_events_payout_fk
            REFERENCES order_payouts
            ON DELETE CASCADE,
    event_id  INTEGER NOT NULL
        CONSTRAINT payout_events_event_fk
            REFERENCES server_events
            ON DELETE CASCADE,
    amount    REAL    NOT NULL,
    CONSTRAINT payout_events_pk
        PRIMARY KEY (payout_id, event_id)
);

CREATE INDEX payout_events_event_index
    ON payout_events (event_id);
