CREATE TABLE job_areas
(
    owner_uuid blob     NOT NULL,
    name       TEXT     NOT NULL,
    node       blob     NOT NULL,
    world      TEXT     NOT NULL,
    min_x      INTEGER  NOT NULL,
    min_y      INTEGER  NOT NULL,
    min_z      INTEGER  NOT NULL,
    max_x      INTEGER  NOT NULL,
    max_y      INTEGER  NOT NULL,
    max_z      INTEGER  NOT NULL,
    created    datetime NOT NULL,
    CONSTRAINT job_areas_pk
        PRIMARY KEY (owner_uuid, name)
);
