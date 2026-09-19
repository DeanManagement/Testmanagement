-- PRD-047: where a bug was found (the step, besides the result), where else it showed up, and when
-- it was resolved, so the dashboard trend is one indexed query instead of a scan of the audit log.
ALTER TABLE bug_reports ADD COLUMN step_result_id UUID REFERENCES step_results(id) ON DELETE SET NULL;
ALTER TABLE bug_reports ADD COLUMN resolved_at TIMESTAMP;
CREATE INDEX idx_bug_reports_step_result ON bug_reports(step_result_id);
CREATE INDEX idx_bug_reports_project_created ON bug_reports(project_id, created_at);
CREATE INDEX idx_bug_reports_project_resolved ON bug_reports(project_id, resolved_at);

-- Best effort: a bug resolved before this column existed counts as resolved when last updated.
UPDATE bug_reports SET resolved_at = updated_at WHERE status IN ('RESOLVED', 'CLOSED');

CREATE TABLE bug_report_links (
    id             UUID PRIMARY KEY,
    bug_report_id  UUID NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    test_result_id UUID NOT NULL REFERENCES test_results(id) ON DELETE CASCADE,
    step_result_id UUID REFERENCES step_results(id) ON DELETE SET NULL,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    CONSTRAINT uq_bug_report_links UNIQUE (bug_report_id, test_result_id)
);
CREATE INDEX idx_bug_report_links_result ON bug_report_links(test_result_id);
CREATE INDEX idx_bug_report_links_step ON bug_report_links(step_result_id);
