CREATE TABLE short_urls (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    original_url VARCHAR(2048) NOT NULL,
    custom_alias BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(128)
);

CREATE INDEX idx_short_urls_active ON short_urls (active);

CREATE TABLE click_events (
    id VARCHAR(36) PRIMARY KEY,
    short_url_id VARCHAR(36) NOT NULL,
    clicked_at TIMESTAMP NOT NULL,
    ip_hash VARCHAR(64),
    user_agent VARCHAR(512),
    referrer VARCHAR(1024),
    CONSTRAINT fk_click_short_url FOREIGN KEY (short_url_id) REFERENCES short_urls (id)
);

CREATE INDEX idx_click_events_url ON click_events (short_url_id);
CREATE INDEX idx_click_events_clicked_at ON click_events (clicked_at);

CREATE TABLE workflow_runs (
    id VARCHAR(36) PRIMARY KEY,
    scenario VARCHAR(32) NOT NULL,
    definition_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    autonomy_mode VARCHAR(32) NOT NULL,
    context_json CLOB NOT NULL,
    stop_requested BOOLEAN NOT NULL DEFAULT FALSE,
    context_version INT NOT NULL DEFAULT 1,
    error_message VARCHAR(2000),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE stage_runs (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    stage_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INT NOT NULL DEFAULT 0,
    input_json CLOB,
    output_json CLOB,
    error_message VARCHAR(2000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_stage_run FOREIGN KEY (run_id) REFERENCES workflow_runs (id)
);

CREATE INDEX idx_stage_runs_run ON stage_runs (run_id);

CREATE TABLE approvals (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    stage_id VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    decided_by VARCHAR(128),
    comment VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP,
    CONSTRAINT fk_approval_run FOREIGN KEY (run_id) REFERENCES workflow_runs (id)
);

CREATE INDEX idx_approvals_run ON approvals (run_id);

CREATE TABLE decision_records (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    actor VARCHAR(32) NOT NULL,
    action VARCHAR(128) NOT NULL,
    rationale CLOB NOT NULL,
    payload_json CLOB,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_decision_run FOREIGN KEY (run_id) REFERENCES workflow_runs (id)
);

CREATE INDEX idx_decisions_run ON decision_records (run_id);
