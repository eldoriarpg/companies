ALTER TABLE system_order_decisions
    ADD COLUMN outcome TEXT;

ALTER TABLE system_order_decisions
    ADD COLUMN ended_at datetime;
