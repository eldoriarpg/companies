CREATE TABLE server_event_notices
(
    event_id    INTEGER  NOT NULL
        CONSTRAINT server_event_notices_event_fk
            REFERENCES server_events
            ON DELETE CASCADE,
    player_uuid blob     NOT NULL,
    noticed     datetime NOT NULL,
    CONSTRAINT server_event_notices_pk
        PRIMARY KEY (event_id, player_uuid)
);

CREATE INDEX server_events_template_index
    ON server_events (template, starts);
