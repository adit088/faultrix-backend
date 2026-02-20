CREATE TABLE webhook_configs (
                                 id              BIGSERIAL PRIMARY KEY,
                                 organization_id BIGINT NOT NULL,
                                 name            VARCHAR(100) NOT NULL,
                                 url             VARCHAR(500) NOT NULL,
                                 secret          VARCHAR(128),
                                 enabled         BOOLEAN NOT NULL DEFAULT TRUE,
                                 on_injection    BOOLEAN NOT NULL DEFAULT TRUE,
                                 on_skipped      BOOLEAN NOT NULL DEFAULT FALSE,
                                 chaos_types     VARCHAR(200),
                                 created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 version         BIGINT NOT NULL DEFAULT 0,

                                 CONSTRAINT fk_webhook_configs_org
                                     FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE INDEX idx_webhook_configs_org ON webhook_configs(organization_id, enabled);

CREATE TABLE webhook_deliveries (
                                    id             BIGSERIAL PRIMARY KEY,
                                    webhook_id     BIGINT NOT NULL,
                                    chaos_event_id BIGINT NOT NULL,
                                    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                    http_status    INT,
                                    attempt        INT NOT NULL DEFAULT 1,
                                    error_message  VARCHAR(500),
                                    delivered_at   TIMESTAMP,
                                    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_deliveries_webhook FOREIGN KEY (webhook_id) REFERENCES webhook_configs(id),
                                    CONSTRAINT fk_deliveries_event   FOREIGN KEY (chaos_event_id) REFERENCES chaos_events(id)
);

CREATE INDEX idx_webhook_deliveries_webhook ON webhook_deliveries(webhook_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_status  ON webhook_deliveries(status, created_at DESC);