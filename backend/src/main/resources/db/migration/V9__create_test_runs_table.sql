CREATE TABLE test_runs (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    environment VARCHAR(255),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    project_id UUID NOT NULL,
    executor_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_test_runs_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_test_runs_executor FOREIGN KEY (executor_id) REFERENCES users(id)
);
