CREATE TABLE test_suite_test_cases (
    test_suite_id UUID NOT NULL,
    test_case_id UUID NOT NULL,
    CONSTRAINT fk_tstc_test_suite FOREIGN KEY (test_suite_id) REFERENCES test_suites(id),
    CONSTRAINT fk_tstc_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id),
    PRIMARY KEY (test_suite_id, test_case_id)
);
