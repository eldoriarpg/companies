CREATE TABLE company_perks
(
    company_id INTEGER  NOT NULL
        CONSTRAINT company_perks_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    perk       TEXT     NOT NULL,
    bought     datetime NOT NULL,
    CONSTRAINT company_perks_pk
        PRIMARY KEY (company_id, perk)
);
