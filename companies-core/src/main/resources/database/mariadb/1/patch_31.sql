ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS reward_value DOUBLE NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS reward_minutes INT NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS reward_funding VARCHAR(16) NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS reward_account VARCHAR(255) NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS reward_budget DOUBLE NULL;

ALTER TABLE event_offers
    ADD COLUMN IF NOT EXISTS reward_event_id INT NULL;

CREATE INDEX IF NOT EXISTS event_offers_event_index
    ON event_offers (event_id);
