-- PRD-053 §8 (TM28): the cases a test plan intends to execute, whether or not a run holds them yet.
CREATE TABLE test_plan_cases (
    test_plan_id UUID NOT NULL,
    test_case_id UUID NOT NULL,
    CONSTRAINT fk_tpc_test_plan FOREIGN KEY (test_plan_id) REFERENCES test_plans(id) ON DELETE CASCADE,
    CONSTRAINT fk_tpc_test_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE CASCADE,
    PRIMARY KEY (test_plan_id, test_case_id)
);
CREATE INDEX idx_tpc_test_case ON test_plan_cases (test_case_id);

-- Per-plan opt-in: the release gate's coverage criterion measures the plan's scope instead of
-- requirement coverage. Off for every existing plan, so no gate moves.
ALTER TABLE test_plans ADD COLUMN gate_coverage_on_scope BOOLEAN DEFAULT FALSE NOT NULL;
