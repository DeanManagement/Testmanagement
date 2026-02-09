CREATE TABLE test_case_labels (
    test_case_id UUID NOT NULL,
    label VARCHAR(255) NOT NULL,
    CONSTRAINT fk_test_case_labels_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id),
    CONSTRAINT uq_test_case_labels UNIQUE (test_case_id, label)
);
