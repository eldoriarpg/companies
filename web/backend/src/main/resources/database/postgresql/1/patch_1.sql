CREATE TABLE web_schema.gateway_state
(
    id       INT PRIMARY KEY CHECK (id = 1),
    alive_at TIMESTAMPTZ NOT NULL
);
