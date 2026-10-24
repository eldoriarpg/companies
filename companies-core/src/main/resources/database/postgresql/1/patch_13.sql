CREATE TABLE IF NOT EXISTS companies_schema.companies_utc
(
    id             INTEGER NOT NULL
        CONSTRAINT companies_utc_pk
            PRIMARY KEY,
    converted_from TEXT    NOT NULL
);
