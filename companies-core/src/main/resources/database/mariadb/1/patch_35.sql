ALTER TABLE company_areas
    DROP FOREIGN KEY IF EXISTS company_areas_companies_id_fk;

CREATE TABLE IF NOT EXISTS company_area_builders
(
    area_id     INT        NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    CONSTRAINT company_area_builders_pk
        PRIMARY KEY (area_id, player_uuid),
    CONSTRAINT company_area_builders_area_fk
        FOREIGN KEY (area_id) REFERENCES company_areas (id)
            ON DELETE CASCADE
);
