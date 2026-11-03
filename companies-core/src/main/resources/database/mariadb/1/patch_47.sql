CREATE TABLE IF NOT EXISTS company_perks
(
    company_id INT         NOT NULL,
    perk       VARCHAR(64) NOT NULL,
    bought     DATETIME    NOT NULL,
    CONSTRAINT company_perks_pk
        PRIMARY KEY (company_id, perk),
    CONSTRAINT company_perks_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);
