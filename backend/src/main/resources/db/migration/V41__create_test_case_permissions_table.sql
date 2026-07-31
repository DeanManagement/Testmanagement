CREATE TABLE test_case_permissions (
    id UUID PRIMARY KEY,
    test_case_id UUID NOT NULL,
    user_id UUID NOT NULL,
    can_edit BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_test_case_permissions_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE CASCADE,
    CONSTRAINT fk_test_case_permissions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_test_case_permissions UNIQUE (test_case_id, user_id)
);

CREATE INDEX idx_test_case_permissions_test_case ON test_case_permissions(test_case_id);
CREATE INDEX idx_test_case_permissions_user ON test_case_permissions(user_id);
