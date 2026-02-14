CREATE TABLE test_plans (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    target_date DATE,
    project_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_test_plans_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);
CREATE INDEX idx_test_plans_project ON test_plans(project_id);

ALTER TABLE test_runs ADD COLUMN test_plan_id UUID;
ALTER TABLE test_runs ADD CONSTRAINT fk_test_runs_test_plan FOREIGN KEY (test_plan_id) REFERENCES test_plans(id) ON DELETE SET NULL;
