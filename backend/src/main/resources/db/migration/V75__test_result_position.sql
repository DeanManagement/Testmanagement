-- TM21: test cases in a run can be put in the order they should be executed.
-- Existing results are numbered in the order they were created, which is the order the run lists
-- them in today (results had no explicit order, and rows come back in insertion order).
ALTER TABLE test_results ADD COLUMN position INT NOT NULL DEFAULT 0;

UPDATE test_results SET position = (
    SELECT COUNT(*) FROM test_results earlier
    WHERE earlier.test_run_id = test_results.test_run_id
      AND (earlier.created_at < test_results.created_at
           OR (earlier.created_at = test_results.created_at AND earlier.id < test_results.id)));

CREATE INDEX idx_test_results_run_position ON test_results (test_run_id, position);
