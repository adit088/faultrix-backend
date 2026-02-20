-- Performance Indexes for Production Scale

CREATE INDEX IF NOT EXISTS idx_chaos_rules_org_id_sort
    ON chaos_rules(organization_id, id);

CREATE INDEX IF NOT EXISTS idx_chaos_rules_org_enabled_id
    ON chaos_rules(organization_id, enabled, id);

CREATE INDEX IF NOT EXISTS idx_chaos_rules_org_tags
    ON chaos_rules(organization_id, tags);

CREATE INDEX IF NOT EXISTS idx_chaos_rules_created_at
    ON chaos_rules(organization_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chaos_rules_updated_at
    ON chaos_rules(organization_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chaos_rules_covering
    ON chaos_rules(organization_id, enabled, target, id, failure_rate, max_delay_ms);