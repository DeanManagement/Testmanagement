ALTER TABLE projects ADD COLUMN next_test_run_number INT NOT NULL DEFAULT 1;

ALTER TABLE test_runs ADD COLUMN test_run_key VARCHAR(30) DEFAULT 'MIGRATING';

-- Backfill existing test runs with a unique key based on row number
UPDATE test_runs t SET test_run_key = (
    SELECT p.project_key || '-Run-' || CAST(
        (SELECT COUNT(*) FROM test_runs t2 WHERE t2.project_id = t.project_id AND t2.created_at <= t.created_at) AS VARCHAR
    ) FROM projects p WHERE p.id = t.project_id
) WHERE t.test_run_key = 'MIGRATING';

-- Update project counters
UPDATE projects p SET next_test_run_number = (
    SELECT COALESCE(COUNT(*), 0) + 1 FROM test_runs t WHERE t.project_id = p.id
);

ALTER TABLE test_runs ALTER COLUMN test_run_key SET NOT NULL;
ALTER TABLE test_runs ALTER COLUMN test_run_key SET DEFAULT NULL;
CREATE UNIQUE INDEX idx_test_runs_key ON test_runs(test_run_key);
