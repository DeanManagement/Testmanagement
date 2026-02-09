CREATE TABLE step_results (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    actual_result TEXT,
    test_result_id UUID NOT NULL,
    test_step_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_step_results_test_result FOREIGN KEY (test_result_id) REFERENCES test_results(id) ON DELETE CASCADE,
    CONSTRAINT fk_step_results_test_step FOREIGN KEY (test_step_id) REFERENCES test_steps(id)
);
