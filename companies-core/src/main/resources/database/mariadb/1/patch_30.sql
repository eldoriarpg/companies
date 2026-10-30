CREATE TABLE IF NOT EXISTS server_event_notices
(
    event_id    INT        NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    noticed     DATETIME   NOT NULL,
    CONSTRAINT server_event_notices_pk
        PRIMARY KEY (event_id, player_uuid),
    CONSTRAINT server_event_notices_event_fk
        FOREIGN KEY (event_id) REFERENCES server_events (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS server_events_template_index
    ON server_events (template, starts);
