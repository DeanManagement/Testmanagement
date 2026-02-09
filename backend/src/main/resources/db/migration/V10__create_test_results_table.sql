CREATE TABLE test_results (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    comment TEXT,
    defect_link VARCHAR(500),
    test_run_id UUID NOT NULL,
    test_case_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_test_results_test_run FOREIGN KEY (test_run_id) REFERENCES test_runs(id),
    CONSTRAINT fk_test_results_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id)
);
