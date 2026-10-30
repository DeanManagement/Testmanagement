-- TM49: labels on bug reports, like test cases, and the test plans a bug was found and fixed in.
CREATE TABLE bug_report_labels (
    bug_report_id UUID NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    label VARCHAR(255) NOT NULL,
    CONSTRAINT uq_bug_report_labels UNIQUE (bug_report_id, label)
);

ALTER TABLE bug_reports ADD COLUMN found_in_plan_id UUID REFERENCES test_plans(id) ON DELETE SET NULL;
ALTER TABLE bug_reports ADD COLUMN fixed_in_plan_id UUID REFERENCES test_plans(id) ON DELETE SET NULL;
CREATE INDEX idx_bug_reports_found_in_plan ON bug_reports(found_in_plan_id);
CREATE INDEX idx_bug_reports_fixed_in_plan ON bug_reports(fixed_in_plan_id);
