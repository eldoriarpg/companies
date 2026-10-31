CREATE TABLE company_areas
(
    id         INTEGER  NOT NULL
        CONSTRAINT company_areas_pk
            PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER  NOT NULL
        CONSTRAINT company_areas_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    provider   TEXT     NOT NULL,
    ref        TEXT     NOT NULL,
    world      TEXT     NOT NULL,
    node       blob     NOT NULL,
    source     TEXT     NOT NULL,
    linked_by  blob,
    price      REAL     NOT NULL DEFAULT 0,
    rent_until datetime,
    created    datetime NOT NULL,
    CONSTRAINT company_areas_ref_uindex
        UNIQUE (provider, ref)
);

CREATE INDEX company_areas_company_index
    ON company_areas (company_id);

CREATE INDEX company_areas_node_index
    ON company_areas (node);

CREATE INDEX company_areas_linked_by_index
    ON company_areas (linked_by);

CREATE INDEX company_areas_rent_until_index
    ON company_areas (rent_until);
