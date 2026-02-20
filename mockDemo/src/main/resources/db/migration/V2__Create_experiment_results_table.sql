CREATE TABLE experiment_results (
                                    id                  BIGSERIAL PRIMARY KEY,
                                    target              VARCHAR(100) NOT NULL,
                                    total_requests      INTEGER NOT NULL DEFAULT 0,
                                    failed_requests     INTEGER NOT NULL DEFAULT 0,
                                    delayed_requests    INTEGER NOT NULL DEFAULT 0,
                                    successful_requests INTEGER NOT NULL DEFAULT 0,
                                    success_rate        DOUBLE PRECISION,
                                    status              VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
                                    started_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    completed_at        TIMESTAMP,
                                    duration_seconds    BIGINT,
                                    rule_snapshot       TEXT,
                                    error_message       TEXT,
                                    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_experiment_results_target     ON experiment_results(target);
CREATE INDEX idx_experiment_results_status     ON experiment_results(status);
CREATE INDEX idx_experiment_results_started_at ON experiment_results(started_at);