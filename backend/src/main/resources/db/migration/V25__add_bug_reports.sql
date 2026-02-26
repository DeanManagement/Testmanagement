ALTER TABLE projects ADD COLUMN bug_reports_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE bug_reports (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    steps_to_reproduce TEXT,
    expected_behavior TEXT,
    actual_behavior TEXT,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    environment VARCHAR(255),
    project_id UUID NOT NULL,
    test_result_id UUID,
    test_run_id UUID,
    assignee_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_bug_reports_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_bug_reports_test_result FOREIGN KEY (test_result_id) REFERENCES test_results(id) ON DELETE SET NULL,
    CONSTRAINT fk_bug_reports_test_run FOREIGN KEY (test_run_id) REFERENCES test_runs(id) ON DELETE SET NULL,
    CONSTRAINT fk_bug_reports_assignee FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_bug_reports_project ON bug_reports(project_id);
CREATE INDEX idx_bug_reports_test_result ON bug_reports(test_result_id);
CREATE INDEX idx_bug_reports_status ON bug_reports(status);
