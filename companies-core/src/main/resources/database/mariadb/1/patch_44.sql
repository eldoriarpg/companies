CREATE TABLE IF NOT EXISTS job_schematics
(
    owner_uuid BINARY(16)  NOT NULL,
    name       VARCHAR(32) NOT NULL,
    data       MEDIUMTEXT  NOT NULL,
    created    DATETIME    NOT NULL,
    CONSTRAINT job_schematics_pk
        PRIMARY KEY (owner_uuid, name)
);
