CREATE TABLE company_homes
(
    company_id INTEGER  NOT NULL
        CONSTRAINT company_homes_pk
            PRIMARY KEY
        CONSTRAINT company_homes_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    node       blob     NOT NULL,
    server     TEXT     NOT NULL,
    world      TEXT     NOT NULL,
    x          REAL     NOT NULL,
    y          REAL     NOT NULL,
    z          REAL     NOT NULL,
    yaw        REAL     NOT NULL,
    pitch      REAL     NOT NULL,
    set_by     blob     NOT NULL,
    set_at     datetime NOT NULL
);

CREATE TABLE home_teleports
(
    player_uuid blob     NOT NULL
        CONSTRAINT home_teleports_pk
            PRIMARY KEY,
    company_id  INTEGER  NOT NULL,
    node        blob     NOT NULL,
    expires     datetime NOT NULL
);
