ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS description TEXT NULL;

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS tag VARCHAR(5) NULL;

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS color VARCHAR(32) NULL;

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS join_mode INT DEFAULT 2 NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS companies_tag_uindex
    ON companies (tag);

CREATE TABLE IF NOT EXISTS company_invites
(
    id           INT AUTO_INCREMENT
        PRIMARY KEY,
    company_id   INT        NOT NULL,
    target_uuid  BINARY(16) NOT NULL,
    inviter_uuid BINARY(16) NOT NULL,
    created      DATETIME   NOT NULL,
    expires      DATETIME   NOT NULL,
    CONSTRAINT company_invites_uindex
        UNIQUE (company_id, target_uuid),
    CONSTRAINT company_invites_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS company_invites_target_index
    ON company_invites (target_uuid);

CREATE INDEX IF NOT EXISTS company_invites_expires_index
    ON company_invites (expires);

CREATE TABLE IF NOT EXISTS company_applications
(
    id             INT AUTO_INCREMENT
        PRIMARY KEY,
    company_id     INT        NOT NULL,
    applicant_uuid BINARY(16) NOT NULL,
    message        TEXT       NOT NULL,
    created        DATETIME   NOT NULL,
    expires        DATETIME   NOT NULL,
    CONSTRAINT company_applications_uindex
        UNIQUE (company_id, applicant_uuid),
    CONSTRAINT company_applications_companies_id_fk
        FOREIGN KEY (company_id) REFERENCES companies (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS company_applications_applicant_index
    ON company_applications (applicant_uuid);

CREATE INDEX IF NOT EXISTS company_applications_expires_index
    ON company_applications (expires);
