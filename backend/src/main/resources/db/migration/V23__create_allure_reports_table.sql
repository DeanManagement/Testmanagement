CREATE TABLE allure_reports (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    data BYTEA NOT NULL,
    test_run_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_allure_reports_test_run FOREIGN KEY (test_run_id)
        REFERENCES test_runs(id) ON DELETE CASCADE
);
