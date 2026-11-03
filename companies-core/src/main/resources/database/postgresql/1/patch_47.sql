CREATE TABLE IF NOT EXISTS companies_schema.company_perks
(
    company_id INTEGER     NOT NULL
        CONSTRAINT company_perks_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    perk       VARCHAR(64) NOT NULL,
    bought     TIMESTAMP   NOT NULL,
    CONSTRAINT company_perks_pk
        PRIMARY KEY (company_id, perk)
);
