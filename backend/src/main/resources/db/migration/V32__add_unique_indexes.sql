-- Add unique composite index for test case keys per project
CREATE UNIQUE INDEX IF NOT EXISTS idx_test_case_key_project ON test_cases(project_id, test_case_key);

-- Add unique composite index for test run keys per project
CREATE UNIQUE INDEX IF NOT EXISTS idx_test_run_key_project ON test_runs(project_id, test_run_key);
