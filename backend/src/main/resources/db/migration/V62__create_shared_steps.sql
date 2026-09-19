-- PRD-030: reusable blocks of steps, kept once per project and referenced from test cases.
CREATE TABLE shared_steps (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_shared_steps_project_title UNIQUE (project_id, title)
);

-- A block's steps are ordinary test_steps rows, so step results, step images and their
-- authorisation keep working on one table. A step belongs to a test case or to a block.
ALTER TABLE test_steps ALTER COLUMN test_case_id DROP NOT NULL;
ALTER TABLE test_steps ADD COLUMN shared_step_id UUID REFERENCES shared_steps(id) ON DELETE CASCADE;
-- A case step may instead stand for a whole block. No cascade: a block in use is not deleted.
ALTER TABLE test_steps ADD COLUMN uses_shared_step_id UUID REFERENCES shared_steps(id);
ALTER TABLE test_steps ADD CONSTRAINT ck_test_steps_owner
    CHECK ((test_case_id IS NULL AND shared_step_id IS NOT NULL) OR (test_case_id IS NOT NULL AND shared_step_id IS NULL));
-- One level only: a block's own steps never reference another block.
ALTER TABLE test_steps ADD CONSTRAINT ck_test_steps_no_nested_ref
    CHECK (uses_shared_step_id IS NULL OR shared_step_id IS NULL);
CREATE INDEX idx_test_steps_shared_step ON test_steps(shared_step_id);
CREATE INDEX idx_test_steps_uses_shared_step ON test_steps(uses_shared_step_id);

-- A result's steps now come from several owners, each numbered from 0, so the result keeps its
-- own order. Existing rows take their step's index; a correlated subquery runs on both databases.
ALTER TABLE step_results ADD COLUMN position INT;
UPDATE step_results SET position =
    (SELECT ts.order_index FROM test_steps ts WHERE ts.id = step_results.test_step_id);
