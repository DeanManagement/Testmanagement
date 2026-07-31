CREATE TABLE test_case_versions (
    id UUID PRIMARY KEY,
    test_case_id UUID NOT NULL,
    version_number INT NOT NULL,
    version_at TIMESTAMP NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    preconditions TEXT,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    labels TEXT,
    -- Steps as a JSON array rather than a per-version child table: versions are written once and
    -- read rarely, so a parallel table would add joins and cascade rules for no benefit.
    steps_snapshot TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_test_case_versions_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE CASCADE,
    CONSTRAINT uq_test_case_versions_number UNIQUE (test_case_id, version_number)
);

CREATE INDEX idx_test_case_versions_case ON test_case_versions (test_case_id, version_number DESC);

ALTER TABLE test_cases ADD COLUMN current_version INT NOT NULL DEFAULT 1;

-- Nullable on purpose, and left NULL for existing rows. A result recorded before versioning
-- existed executed some earlier wording that was never captured; stamping it v1 would claim it ran
-- against today's text. For an audit trail an explicit gap is worth more than a confident guess.
ALTER TABLE test_results ADD COLUMN executed_version INT;
