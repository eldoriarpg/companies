CREATE TABLE IF NOT EXISTS company_homes
(
    company_id INT          NOT NULL,
    node       BINARY(16)   NOT NULL,
    server     VARCHAR(64)  NOT NULL,
    world      VARCHAR(64)  NOT NULL,
    x          DOUBLE       NOT NULL,
    y          DOUBLE       NOT NULL,
    z          DOUBLE       NOT NULL,
    yaw        FLOAT        NOT NULL,
    pitch      FLOAT        NOT NULL,
    set_by     BINARY(16)   NOT NULL,
    set_at     DATETIME     NOT NULL,
    CONSTRAINT company_homes_pk
        PRIMARY KEY (company_id),
    CONSTRAINT company_homes_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS home_teleports
(
    player_uuid BINARY(16) NOT NULL,
    company_id  INT        NOT NULL,
    node        BINARY(16) NOT NULL,
    expires     DATETIME   NOT NULL,
    CONSTRAINT home_teleports_pk
        PRIMARY KEY (player_uuid)
);
