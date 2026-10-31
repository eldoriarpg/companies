CREATE TABLE IF NOT EXISTS company_areas
(
    id         INT AUTO_INCREMENT NOT NULL,
    company_id INT                NOT NULL,
    provider   VARCHAR(32)        NOT NULL,
    ref        VARCHAR(255)       NOT NULL,
    world      VARCHAR(64)        NOT NULL,
    node       BINARY(16)         NOT NULL,
    source     VARCHAR(16)        NOT NULL,
    linked_by  BINARY(16)         NULL,
    price      DOUBLE             NOT NULL DEFAULT 0,
    rent_until DATETIME           NULL,
    created    DATETIME           NOT NULL,
    CONSTRAINT company_areas_pk
        PRIMARY KEY (id),
    CONSTRAINT company_areas_ref_uindex
        UNIQUE (provider, ref),
    CONSTRAINT company_areas_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS company_areas_company_index
    ON company_areas (company_id);

CREATE INDEX IF NOT EXISTS company_areas_node_index
    ON company_areas (node);

CREATE INDEX IF NOT EXISTS company_areas_linked_by_index
    ON company_areas (linked_by);

CREATE INDEX IF NOT EXISTS company_areas_rent_until_index
    ON company_areas (rent_until);
