CREATE TABLE chaos_events (
                              id              BIGSERIAL PRIMARY KEY,
                              organization_id BIGINT NOT NULL,
                              chaos_rule_id   BIGINT,
                              target          VARCHAR(100) NOT NULL,
                              request_id      VARCHAR(64)  NOT NULL,
                              chaos_type      VARCHAR(20)  NOT NULL,
                              injected        BOOLEAN NOT NULL DEFAULT TRUE,
                              http_status     INT,
                              delay_ms        INT NOT NULL DEFAULT 0,
                              failure_rate    DOUBLE PRECISION NOT NULL,
                              blast_radius    DOUBLE PRECISION NOT NULL,
                              occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_chaos_events_org
                                  FOREIGN KEY (organization_id) REFERENCES organizations(id),
                              CONSTRAINT fk_chaos_events_rule
                                  FOREIGN KEY (chaos_rule_id) REFERENCES chaos_rules(id) ON DELETE SET NULL
);

CREATE INDEX idx_chaos_events_org_occurred ON chaos_events(organization_id, occurred_at DESC);
CREATE INDEX idx_chaos_events_org_target   ON chaos_events(organization_id, target, occurred_at DESC);
CREATE INDEX idx_chaos_events_org_type     ON chaos_events(organization_id, chaos_type, occurred_at DESC);
CREATE INDEX idx_chaos_events_request_id   ON chaos_events(request_id);
CREATE INDEX idx_chaos_events_analytics    ON chaos_events(organization_id, occurred_at DESC, chaos_type, target, injected);