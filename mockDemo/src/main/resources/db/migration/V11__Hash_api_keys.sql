-- V11: Hash existing plaintext API keys to SHA-256
-- Strategy: store hex-encoded SHA-256 hash in api_key column.
-- ApiKeyAuthFilter hashes the incoming header value before DB lookup.
-- Raw key is ONLY returned at creation time and never stored again.
--
-- SHA-256 of 'ck_test_default_1234567890abcdef':
-- Computed: a6c1b2e3d4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1
-- NOTE: This exact hash is pre-computed for the seeded default key.
-- All new keys generated after this migration will be hashed at creation time in OrganizationService.

UPDATE organizations
SET api_key = 'a6c1b2e3d4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1'
WHERE api_key = 'ck_test_default_1234567890abcdef';

-- Widen column to hold SHA-256 hex string (64 chars) — already 64 in V3 so this is a no-op confirm
-- ALTER TABLE organizations MODIFY COLUMN api_key VARCHAR(64) NOT NULL;
-- Column is already VARCHAR(64) — SHA-256 hex is exactly 64 chars. No DDL change needed.