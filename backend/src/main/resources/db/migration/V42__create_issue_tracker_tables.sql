CREATE TABLE issue_tracker_configs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    project_ref VARCHAR(300) NOT NULL,
    api_token_encrypted TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_error TEXT,
    last_error_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_issue_tracker_configs_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_issue_tracker_configs_project UNIQUE (project_id)
);

CREATE TABLE issue_links (
    id UUID PRIMARY KEY,
    test_result_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    external_id VARCHAR(300) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    title VARCHAR(500),
    state VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    state_checked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_issue_links_test_result FOREIGN KEY (test_result_id) REFERENCES test_results(id) ON DELETE CASCADE,
    CONSTRAINT uq_issue_links_result_external UNIQUE (test_result_id, external_id)
);

CREATE INDEX idx_issue_links_test_result ON issue_links (test_result_id);
CREATE INDEX idx_issue_links_state_checked ON issue_links (state_checked_at);
