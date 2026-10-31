ALTER TABLE companies_schema.company_areas
    DROP CONSTRAINT IF EXISTS company_areas_companies_id_fk;

CREATE TABLE IF NOT EXISTS companies_schema.company_area_builders
(
    area_id     INTEGER NOT NULL
        CONSTRAINT company_area_builders_area_fk
            REFERENCES companies_schema.company_areas
            ON DELETE CASCADE,
    player_uuid BYTEA   NOT NULL,
    CONSTRAINT company_area_builders_pk
        PRIMARY KEY (area_id, player_uuid)
);
