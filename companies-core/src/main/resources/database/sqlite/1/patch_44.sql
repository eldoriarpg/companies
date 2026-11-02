CREATE TABLE job_schematics
(
    owner_uuid blob     NOT NULL,
    name       TEXT     NOT NULL,
    data       TEXT     NOT NULL,
    created    datetime NOT NULL,
    CONSTRAINT job_schematics_pk
        PRIMARY KEY (owner_uuid, name)
);
