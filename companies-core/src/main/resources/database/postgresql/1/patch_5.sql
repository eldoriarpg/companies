ALTER TABLE companies_schema.companies
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE companies_schema.companies
    ADD COLUMN IF NOT EXISTS tag TEXT;

ALTER TABLE companies_schema.companies
    ADD COLUMN IF NOT EXISTS color TEXT;

ALTER TABLE companies_schema.companies
    ADD COLUMN IF NOT EXISTS join_mode INTEGER DEFAULT 2 NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS companies_tag_uindex
    ON companies_schema.companies (tag);

CREATE TABLE IF NOT EXISTS companies_schema.company_invites
(
    id           SERIAL
        CONSTRAINT company_invites_pk
            PRIMARY KEY,
    company_id   INTEGER   NOT NULL
        CONSTRAINT company_invites_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    target_uuid  bytea     NOT NULL,
    inviter_uuid bytea     NOT NULL,
    created      TIMESTAMP NOT NULL,
    expires      TIMESTAMP NOT NULL,
    CONSTRAINT company_invites_uindex
        UNIQUE (company_id, target_uuid)
);

CREATE INDEX IF NOT EXISTS company_invites_target_index
    ON companies_schema.company_invites (target_uuid);

CREATE INDEX IF NOT EXISTS company_invites_expires_index
    ON companies_schema.company_invites (expires);

CREATE TABLE IF NOT EXISTS companies_schema.company_applications
(
    id             SERIAL
        CONSTRAINT company_applications_pk
            PRIMARY KEY,
    company_id     INTEGER   NOT NULL
        CONSTRAINT company_applications_companies_id_fk
            REFERENCES companies_schema.companies
            ON DELETE CASCADE,
    applicant_uuid bytea     NOT NULL,
    message        TEXT      NOT NULL,
    created        TIMESTAMP NOT NULL,
    expires        TIMESTAMP NOT NULL,
    CONSTRAINT company_applications_uindex
        UNIQUE (company_id, applicant_uuid)
);

CREATE INDEX IF NOT EXISTS company_applications_applicant_index
    ON companies_schema.company_applications (applicant_uuid);

CREATE INDEX IF NOT EXISTS company_applications_expires_index
    ON companies_schema.company_applications (expires);
