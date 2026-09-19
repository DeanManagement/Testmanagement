-- PRD-044: files attached to a test case (sample inputs, specs, expected outputs). Named for the
-- general case: PRD-051 adds bug reports as a second owner to this same table.
CREATE TABLE attachments (
    id            UUID PRIMARY KEY,
    test_case_id  UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    data          BYTEA        NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    created_by    UUID,
    updated_by    UUID
);
CREATE INDEX idx_attachments_test_case ON attachments(test_case_id);
