CREATE TABLE IF NOT EXISTS companies_schema.company_areas
(
    id         SERIAL           NOT NULL
        CONSTRAINT company_areas_pk
            PRIMARY KEY,
    company_id INTEGER          NOT NULL
        CONSTRAINT company_areas_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    provider   VARCHAR(32)      NOT NULL,
    ref        VARCHAR(255)     NOT NULL,
    world      VARCHAR(64)      NOT NULL,
    node       BYTEA            NOT NULL,
    source     VARCHAR(16)      NOT NULL,
    linked_by  BYTEA,
    price      DOUBLE PRECISION NOT NULL DEFAULT 0,
    rent_until TIMESTAMP,
    created    TIMESTAMP        NOT NULL,
    CONSTRAINT company_areas_ref_uindex
        UNIQUE (provider, ref)
);

CREATE INDEX IF NOT EXISTS company_areas_company_index
    ON companies_schema.company_areas (company_id);

CREATE INDEX IF NOT EXISTS company_areas_node_index
    ON companies_schema.company_areas (node);

CREATE INDEX IF NOT EXISTS company_areas_linked_by_index
    ON companies_schema.company_areas (linked_by);

CREATE INDEX IF NOT EXISTS company_areas_rent_until_index
    ON companies_schema.company_areas (rent_until);
