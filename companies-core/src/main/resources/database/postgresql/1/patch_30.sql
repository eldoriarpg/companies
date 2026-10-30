CREATE TABLE IF NOT EXISTS companies_schema.server_event_notices
(
    event_id    INTEGER   NOT NULL
        CONSTRAINT server_event_notices_event_fk
            REFERENCES companies_schema.server_events
            ON DELETE CASCADE,
    player_uuid BYTEA     NOT NULL,
    noticed     TIMESTAMP NOT NULL,
    CONSTRAINT server_event_notices_pk
        PRIMARY KEY (event_id, player_uuid)
);

CREATE INDEX IF NOT EXISTS server_events_template_index
    ON companies_schema.server_events (template, starts);
