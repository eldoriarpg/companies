CREATE TABLE IF NOT EXISTS companies_schema.company_homes
(
    company_id INTEGER          NOT NULL
        CONSTRAINT company_homes_pk
            PRIMARY KEY
        CONSTRAINT company_homes_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    node       BYTEA            NOT NULL,
    server     VARCHAR(64)      NOT NULL,
    world      VARCHAR(64)      NOT NULL,
    x          DOUBLE PRECISION NOT NULL,
    y          DOUBLE PRECISION NOT NULL,
    z          DOUBLE PRECISION NOT NULL,
    yaw        REAL             NOT NULL,
    pitch      REAL             NOT NULL,
    set_by     BYTEA            NOT NULL,
    set_at     TIMESTAMP        NOT NULL
);

CREATE TABLE IF NOT EXISTS companies_schema.home_teleports
(
    player_uuid BYTEA     NOT NULL
        CONSTRAINT home_teleports_pk
            PRIMARY KEY,
    company_id  INTEGER   NOT NULL,
    node        BYTEA     NOT NULL,
    expires     TIMESTAMP NOT NULL
);
