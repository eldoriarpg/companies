CREATE TABLE IF NOT EXISTS companies_schema.job_areas
(
    owner_uuid BYTEA       NOT NULL,
    name       VARCHAR(32) NOT NULL,
    node       BYTEA       NOT NULL,
    world      VARCHAR(64) NOT NULL,
    min_x      INTEGER     NOT NULL,
    min_y      INTEGER     NOT NULL,
    min_z      INTEGER     NOT NULL,
    max_x      INTEGER     NOT NULL,
    max_y      INTEGER     NOT NULL,
    max_z      INTEGER     NOT NULL,
    created    TIMESTAMP   NOT NULL,
    CONSTRAINT job_areas_pk
        PRIMARY KEY (owner_uuid, name)
);
