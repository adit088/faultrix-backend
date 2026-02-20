ALTER TABLE webhook_deliveries ADD COLUMN next_retry_at TIMESTAMP;
ALTER TABLE webhook_deliveries ADD COLUMN max_attempts  INT NOT NULL DEFAULT 3;

-- PostgreSQL supports partial indexes, unlike H2
CREATE INDEX idx_webhook_deliveries_retry ON webhook_deliveries(next_retry_at) WHERE status = 'FAILED';

CREATE TABLE chaos_schedules (
                                 id              BIGSERIAL PRIMARY KEY,
                                 chaos_rule_id   BIGINT       NOT NULL,
                                 organization_id BIGINT       NOT NULL,
                                 name            VARCHAR(100) NOT NULL,
                                 enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
                                 days_of_week    VARCHAR(20)  NOT NULL DEFAULT '1,2,3,4,5,6,7',
                                 start_time      VARCHAR(5)   NOT NULL DEFAULT '00:00',
                                 end_time        VARCHAR(5)   NOT NULL DEFAULT '23:59',
                                 active_from     TIMESTAMP,
                                 active_until    TIMESTAMP,
                                 created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 version         BIGINT       NOT NULL DEFAULT 0,

                                 CONSTRAINT fk_chaos_schedules_rule FOREIGN KEY (chaos_rule_id) REFERENCES chaos_rules(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_chaos_schedules_org  FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE INDEX idx_chaos_schedules_rule ON chaos_schedules(chaos_rule_id, enabled);
CREATE INDEX idx_chaos_schedules_org  ON chaos_schedules(organization_id, enabled);

ALTER TABLE chaos_rules ADD COLUMN target_pattern VARCHAR(200);
ALTER TABLE chaos_rules ADD COLUMN targeting_mode VARCHAR(10) NOT NULL DEFAULT 'EXACT';

CREATE INDEX idx_chaos_rules_targeting_mode ON chaos_rules(organization_id, targeting_mode, enabled);