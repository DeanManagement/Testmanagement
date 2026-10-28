-- TM23: a result can be assigned to a tester, so a run's cases can be split between people.
-- Null means "whoever executes the run" (test_runs.executor_id).
ALTER TABLE test_results ADD COLUMN assignee_id UUID;
ALTER TABLE test_results ADD CONSTRAINT fk_test_results_assignee FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX idx_test_results_assignee ON test_results (assignee_id);
