ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS reward_value DOUBLE PRECISION;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS reward_minutes INTEGER;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS reward_funding TEXT;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS reward_account TEXT;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS reward_budget DOUBLE PRECISION;

ALTER TABLE companies_schema.event_offers
    ADD COLUMN IF NOT EXISTS reward_event_id INTEGER;

CREATE INDEX IF NOT EXISTS event_offers_event_index
    ON companies_schema.event_offers (event_id);
