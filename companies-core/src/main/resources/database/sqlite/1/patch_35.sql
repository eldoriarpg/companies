CREATE TABLE company_areas_new
(
    id         INTEGER  NOT NULL
        CONSTRAINT company_areas_pk
            PRIMARY KEY AUTOINCREMENT,
    company_id INTEGER  NOT NULL,
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

INSERT INTO company_areas_new(id, company_id, provider, ref, world, node, source, linked_by, price, rent_until, created)
SELECT id, company_id, provider, ref, world, node, source, linked_by, price, rent_until, created
FROM company_areas;

DROP TABLE company_areas;

ALTER TABLE company_areas_new
    RENAME TO company_areas;

CREATE INDEX company_areas_company_index
    ON company_areas (company_id);

CREATE INDEX company_areas_node_index
    ON company_areas (node);

CREATE INDEX company_areas_linked_by_index
    ON company_areas (linked_by);

CREATE INDEX company_areas_rent_until_index
    ON company_areas (rent_until);

CREATE TABLE company_area_builders
(
    area_id     INTEGER NOT NULL
        CONSTRAINT company_area_builders_area_fk
            REFERENCES company_areas
            ON DELETE CASCADE,
    player_uuid blob    NOT NULL,
    CONSTRAINT company_area_builders_pk
        PRIMARY KEY (area_id, player_uuid)
);
