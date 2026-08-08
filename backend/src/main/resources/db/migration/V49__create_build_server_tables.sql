-- PRD-024: build server integration.
-- Servers are global (no project FK); projects gain access to workflows via assignments.

CREATE TABLE build_server_configs (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(30) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    api_token_encrypted TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_error TEXT,
    last_error_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_build_server_configs_name UNIQUE (name)
);

CREATE TABLE build_workflows (
    id UUID PRIMARY KEY,
    build_server_config_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    repo_ref VARCHAR(300) NOT NULL,
    workflow_ref VARCHAR(300),
    default_ref VARCHAR(200),
    default_parameters TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_build_workflows_server FOREIGN KEY (build_server_config_id)
        REFERENCES build_server_configs(id) ON DELETE CASCADE,
    CONSTRAINT uq_build_workflows_server_name UNIQUE (build_server_config_id, name)
);

CREATE TABLE project_build_workflows (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    build_workflow_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_project_build_workflows_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_build_workflows_workflow FOREIGN KEY (build_workflow_id)
        REFERENCES build_workflows(id) ON DELETE CASCADE,
    CONSTRAINT uq_project_build_workflows UNIQUE (project_id, build_workflow_id)
);

-- Run history outlives its workflow (SET NULL + denormalised workflow_name) and its test run,
-- but not its project.
CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY,
    build_workflow_id UUID,
    project_id UUID NOT NULL,
    workflow_name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRIGGERED',
    external_run_id VARCHAR(200),
    external_url VARCHAR(1000),
    triggered_ref VARCHAR(200),
    parameters TEXT,
    test_run_id UUID,
    error_message TEXT,
    last_polled_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_pipeline_runs_workflow FOREIGN KEY (build_workflow_id)
        REFERENCES build_workflows(id) ON DELETE SET NULL,
    CONSTRAINT fk_pipeline_runs_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_runs_test_run FOREIGN KEY (test_run_id)
        REFERENCES test_runs(id) ON DELETE SET NULL
);

CREATE INDEX idx_project_build_workflows_project ON project_build_workflows (project_id);
CREATE INDEX idx_pipeline_runs_project_created ON pipeline_runs (project_id, created_at);
CREATE INDEX idx_pipeline_runs_status ON pipeline_runs (status);
