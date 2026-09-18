-- PRD-036: an estimate per test case, and a measured duration and execution time per result.
-- All nullable. executed_at is deliberately NOT backfilled from updated_at: that column moves on
-- comment and defect-link edits, so a backfill would invent execution times (the reasoning V44
-- used for executed_version). Burn-downs of older plans simply start at this migration.
ALTER TABLE test_cases ADD COLUMN estimate_minutes INT;
ALTER TABLE test_cases ADD CONSTRAINT ck_test_cases_estimate CHECK (estimate_minutes BETWEEN 1 AND 1440);

-- Milliseconds, because CI durations are sub-second.
ALTER TABLE test_results ADD COLUMN duration_ms BIGINT;
ALTER TABLE test_results ADD CONSTRAINT ck_test_results_duration CHECK (duration_ms >= 0);
ALTER TABLE test_results ADD COLUMN executed_at TIMESTAMP;
CREATE INDEX idx_test_results_executed_at ON test_results (test_run_id, executed_at);

ALTER TABLE test_case_versions ADD COLUMN estimate_minutes INT;
