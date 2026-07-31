-- Postgres does not auto-index FK columns. These back user-facing queries
-- ("My Queue", "assigned to me", bug-report filters) and ON DELETE SET NULL
-- cascades that would otherwise scan the whole child table.

CREATE INDEX idx_test_runs_executor ON test_runs (executor_id);
CREATE INDEX idx_test_runs_completed_by ON test_runs (completed_by_id);
CREATE INDEX idx_test_runs_test_plan ON test_runs (test_plan_id);
CREATE INDEX idx_test_plans_assignee ON test_plans (assignee_id);
CREATE INDEX idx_bug_reports_assignee ON bug_reports (assignee_id);
CREATE INDEX idx_bug_reports_test_run ON bug_reports (test_run_id);
-- Reverse lookup on the join table: PK covers (test_suite_id, test_case_id) only.
CREATE INDEX idx_tstc_test_case ON test_suite_test_cases (test_case_id);
