CREATE TABLE chaos_rules (
                             id              BIGSERIAL PRIMARY KEY,
                             target          VARCHAR(100) NOT NULL UNIQUE,
                             failure_rate    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                             max_delay_ms    BIGINT NOT NULL DEFAULT 0,
                             enabled         BOOLEAN NOT NULL DEFAULT FALSE,
                             description     VARCHAR(500),
                             tags            VARCHAR(200),
                             seed            BIGINT,
                             blast_radius    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
                             created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             created_by      VARCHAR(100),
                             updated_by      VARCHAR(100),
                             version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_chaos_rules_target  ON chaos_rules(target);
CREATE INDEX idx_chaos_rules_enabled ON chaos_rules(enabled);

INSERT INTO chaos_rules (target, failure_rate, max_delay_ms, enabled, description, created_by, updated_by)
VALUES ('default', 0.0, 0, FALSE, 'Default fallback rule', 'system', 'system');