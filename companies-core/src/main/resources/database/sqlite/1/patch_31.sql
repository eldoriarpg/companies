ALTER TABLE event_offers
    ADD COLUMN reward_value REAL;

ALTER TABLE event_offers
    ADD COLUMN reward_minutes INTEGER;

ALTER TABLE event_offers
    ADD COLUMN reward_funding TEXT;

ALTER TABLE event_offers
    ADD COLUMN reward_account TEXT;

ALTER TABLE event_offers
    ADD COLUMN reward_budget REAL;

ALTER TABLE event_offers
    ADD COLUMN reward_event_id INTEGER;

CREATE INDEX event_offers_event_index
    ON event_offers (event_id);
