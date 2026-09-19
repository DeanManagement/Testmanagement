-- PRD-048: who executed a result, set once when it leaves PENDING, so a later edit by someone else
-- (a lead fixing a comment) does not change what the evidence says. No FK, like the other audit ids:
-- a deleted user must not block anything.
ALTER TABLE test_results ADD COLUMN executed_by UUID;

-- Best effort for existing rows: the last editor is the best information there is.
UPDATE test_results SET executed_by = updated_by WHERE executed_at IS NOT NULL;
