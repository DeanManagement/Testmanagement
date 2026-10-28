-- TM05 (PRD-053 §1): documents on a test step, and several evidence files on a step result. Both are
-- further owners of the attachments table, so size, sha256, type checks and the quota stay one rule.
-- A step's files go with the step; a result's evidence with the result.
ALTER TABLE attachments ADD COLUMN test_step_id UUID REFERENCES test_steps(id) ON DELETE CASCADE;
ALTER TABLE attachments ADD COLUMN step_result_id UUID REFERENCES step_results(id) ON DELETE CASCADE;
ALTER TABLE attachments DROP CONSTRAINT ck_attachments_one_owner;
ALTER TABLE attachments ADD CONSTRAINT ck_attachments_one_owner CHECK (
    (CASE WHEN test_case_id IS NULL THEN 0 ELSE 1 END)
  + (CASE WHEN bug_report_id IS NULL THEN 0 ELSE 1 END)
  + (CASE WHEN test_step_id IS NULL THEN 0 ELSE 1 END)
  + (CASE WHEN step_result_id IS NULL THEN 0 ELSE 1 END) = 1);
CREATE INDEX idx_attachments_test_step ON attachments(test_step_id);
CREATE INDEX idx_attachments_step_result ON attachments(step_result_id);
