CREATE TABLE organizations (
                               id         BIGSERIAL PRIMARY KEY,
                               name       VARCHAR(200) NOT NULL,
                               slug       VARCHAR(100) NOT NULL UNIQUE,
                               api_key    VARCHAR(64)  NOT NULL UNIQUE,
                               enabled    BOOLEAN NOT NULL DEFAULT TRUE,
                               plan       VARCHAR(50) NOT NULL DEFAULT 'free',
                               max_rules  INT NOT NULL DEFAULT 10,
                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               version    BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_organizations_slug    ON organizations(slug);
CREATE INDEX idx_organizations_api_key ON organizations(api_key);
CREATE INDEX idx_organizations_enabled ON organizations(enabled);

ALTER TABLE chaos_rules ADD COLUMN organization_id BIGINT;
ALTER TABLE chaos_rules ADD CONSTRAINT fk_chaos_rules_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id);

CREATE INDEX idx_chaos_rules_org_target  ON chaos_rules(organization_id, target);
CREATE INDEX idx_chaos_rules_org_enabled ON chaos_rules(organization_id, enabled);

INSERT INTO organizations (name, slug, api_key, plan, max_rules)
VALUES ('Default Organization', 'default', 'ck_test_default_1234567890abcdef', 'enterprise', 999);

UPDATE chaos_rules SET organization_id = 1 WHERE organization_id IS NULL;

ALTER TABLE chaos_rules ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE chaos_rules DROP CONSTRAINT IF EXISTS chaos_rules_target_key;
ALTER TABLE chaos_rules ADD CONSTRAINT uk_chaos_rules_org_target UNIQUE (organization_id, target);