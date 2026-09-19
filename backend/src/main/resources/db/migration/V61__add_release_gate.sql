-- PRD-037: optional release-gate thresholds on a test plan. NULL means the criterion is not used.
-- One gate per plan with no lifecycle of its own, so plain columns rather than a table.
ALTER TABLE test_plans ADD COLUMN gate_min_pass_rate NUMERIC(5, 2);
ALTER TABLE test_plans ADD COLUMN gate_max_blocker_bugs INT;
ALTER TABLE test_plans ADD COLUMN gate_min_coverage NUMERIC(5, 2);
ALTER TABLE test_plans ADD COLUMN gate_max_flaky INT;

ALTER TABLE test_plans ADD CONSTRAINT ck_test_plans_gate_pass_rate CHECK (gate_min_pass_rate BETWEEN 0 AND 100);
ALTER TABLE test_plans ADD CONSTRAINT ck_test_plans_gate_coverage CHECK (gate_min_coverage BETWEEN 0 AND 100);
ALTER TABLE test_plans ADD CONSTRAINT ck_test_plans_gate_blocker_bugs CHECK (gate_max_blocker_bugs >= 0);
ALTER TABLE test_plans ADD CONSTRAINT ck_test_plans_gate_flaky CHECK (gate_max_flaky >= 0);
