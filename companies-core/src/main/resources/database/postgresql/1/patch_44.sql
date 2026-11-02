CREATE TABLE IF NOT EXISTS companies_schema.job_schematics
(
    owner_uuid BYTEA       NOT NULL,
    name       VARCHAR(32) NOT NULL,
    data       TEXT        NOT NULL,
    created    TIMESTAMP   NOT NULL,
    CONSTRAINT job_schematics_pk
        PRIMARY KEY (owner_uuid, name)
);
