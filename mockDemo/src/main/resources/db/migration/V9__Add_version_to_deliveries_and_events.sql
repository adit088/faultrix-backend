-- V9: Add optimistic locking columns
ALTER TABLE webhook_deliveries ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chaos_events ADD COLUMN version BIGINT NOT NULL DEFAULT 0;