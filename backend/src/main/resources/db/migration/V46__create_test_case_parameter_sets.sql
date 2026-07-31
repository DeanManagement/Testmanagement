CREATE TABLE test_case_parameter_sets (
    id UUID PRIMARY KEY,
    test_case_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    -- JSON object of {key: value}. A child table of key/value rows would be more "correct"
    -- relationally but sets are read and written whole, never queried by key.
    values_json TEXT NOT NULL,
    order_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_parameter_sets_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE CASCADE,
    CONSTRAINT uq_parameter_sets_case_name UNIQUE (test_case_id, name)
);

CREATE INDEX idx_parameter_sets_case ON test_case_parameter_sets (test_case_id, order_index);

-- Recorded on the result so a past execution stays reproducible after the template is edited.
ALTER TABLE test_results ADD COLUMN parameter_set_name VARCHAR(200);
ALTER TABLE test_results ADD COLUMN parameter_values_json TEXT;
