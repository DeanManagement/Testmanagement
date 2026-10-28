-- TM07 (PRD-053 §2): typed links between two cases of the same project. PRECONDITION reads
-- "source needs target run first"; RELATES_TO is informational. Deleting either case removes
-- the link; the same-project rule is enforced by the service.
CREATE TABLE test_case_links (
    id             UUID PRIMARY KEY,
    source_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    target_case_id UUID NOT NULL REFERENCES test_cases(id) ON DELETE CASCADE,
    link_type      VARCHAR(20) NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT uq_test_case_links UNIQUE (source_case_id, target_case_id, link_type),
    CONSTRAINT ck_test_case_links_not_self CHECK (source_case_id <> target_case_id)
);
CREATE INDEX idx_test_case_links_target ON test_case_links(target_case_id);
