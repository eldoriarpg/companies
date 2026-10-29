ALTER TABLE event_offers
    ADD COLUMN posted_progress INTEGER NOT NULL DEFAULT 0;

UPDATE event_offers
SET posted_progress = CASE
                          WHEN amount <= 0 OR delivered >= amount THEN 100
                          WHEN delivered * 4 >= amount * 3 THEN 75
                          WHEN delivered * 2 >= amount THEN 50
                          WHEN delivered * 4 >= amount THEN 25
                          ELSE 0
    END
WHERE kind = 'GOAL';

UPDATE event_offers
SET started = starts
WHERE kind = 'GOAL'
  AND started IS NULL
  AND state <> 'SCHEDULED'
  AND state <> 'PAUSED';
