CREATE TABLE test_steps (
    id UUID PRIMARY KEY,
    action TEXT NOT NULL,
    expected_result TEXT,
    order_index INT NOT NULL,
    test_case_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_test_steps_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id)
);
