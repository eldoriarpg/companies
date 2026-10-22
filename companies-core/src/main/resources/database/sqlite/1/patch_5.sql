ALTER TABLE companies
    ADD COLUMN description TEXT;

ALTER TABLE companies
    ADD COLUMN tag TEXT;

ALTER TABLE companies
    ADD COLUMN color TEXT;

ALTER TABLE companies
    ADD COLUMN join_mode INTEGER DEFAULT 2 NOT NULL;

CREATE UNIQUE INDEX companies_tag_uindex
    ON companies (tag);

CREATE TABLE company_invites
(
    id           INTEGER  NOT NULL
        CONSTRAINT company_invites_pk
            PRIMARY KEY AUTOINCREMENT,
    company_id   INTEGER  NOT NULL
        CONSTRAINT company_invites_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    target_uuid  blob     NOT NULL,
    inviter_uuid blob     NOT NULL,
    created      datetime NOT NULL,
    expires      datetime NOT NULL,
    CONSTRAINT company_invites_uindex
        UNIQUE (company_id, target_uuid)
);

CREATE INDEX company_invites_target_index
    ON company_invites (target_uuid);

CREATE INDEX company_invites_expires_index
    ON company_invites (expires);

CREATE TABLE company_applications
(
    id             INTEGER  NOT NULL
        CONSTRAINT company_applications_pk
            PRIMARY KEY AUTOINCREMENT,
    company_id     INTEGER  NOT NULL
        CONSTRAINT company_applications_companies_id_fk
            REFERENCES companies
            ON DELETE CASCADE,
    applicant_uuid blob     NOT NULL,
    message        TEXT     NOT NULL,
    created        datetime NOT NULL,
    expires        datetime NOT NULL,
    CONSTRAINT company_applications_uindex
        UNIQUE (company_id, applicant_uuid)
);

CREATE INDEX company_applications_applicant_index
    ON company_applications (applicant_uuid);

CREATE INDEX company_applications_expires_index
    ON company_applications (expires);
