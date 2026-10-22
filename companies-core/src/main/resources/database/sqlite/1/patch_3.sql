ALTER TABLE order_states
    ADD COLUMN extensions INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE order_states
    ADD COLUMN delivery_bonus INTEGER DEFAULT 0 NOT NULL;

ALTER TABLE order_states
    ADD COLUMN purge_warning datetime;

ALTER TABLE item_returns
    ADD COLUMN kind INTEGER DEFAULT 0 NOT NULL;
