-- V10: No-op migration marker
-- FORMATDATETIME fix is handled at JPQL layer (ChaosEventRepository)
-- Native query replaced with JPQL group-by-hour using function() — no schema changes needed
-- This migration intentionally left as a marker for audit trail
SELECT 1;