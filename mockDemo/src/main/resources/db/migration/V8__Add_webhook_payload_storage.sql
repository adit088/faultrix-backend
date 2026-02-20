-- ── V8: Add payload storage for webhook retry ────────────────────────────────
-- Critical fix: Store request_payload so retryFailedDelivery() can resend

ALTER TABLE webhook_deliveries ADD COLUMN request_payload TEXT;

-- Migration note: Existing rows will have NULL payload (acceptable — they're historical)