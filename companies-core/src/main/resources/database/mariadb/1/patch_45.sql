CREATE TABLE IF NOT EXISTS job_areas
(
    owner_uuid BINARY(16)  NOT NULL,
    name       VARCHAR(32) NOT NULL,
    node       BINARY(16)  NOT NULL,
    world      VARCHAR(64) NOT NULL,
    min_x      INT         NOT NULL,
    min_y      INT         NOT NULL,
    min_z      INT         NOT NULL,
    max_x      INT         NOT NULL,
    max_y      INT         NOT NULL,
    max_z      INT         NOT NULL,
    created    DATETIME    NOT NULL,
    CONSTRAINT job_areas_pk
        PRIMARY KEY (owner_uuid, name)
);
