-- TM50: a bug report handed over to the issue tracker keeps its work item as an issue link, and
-- takes its status from it. A link belongs to a test result or to a bug report, never both.
ALTER TABLE issue_links ALTER COLUMN test_result_id DROP NOT NULL;
ALTER TABLE issue_links ADD COLUMN bug_report_id UUID REFERENCES bug_reports(id) ON DELETE CASCADE;
ALTER TABLE issue_links ADD CONSTRAINT uq_issue_links_bug_report UNIQUE (bug_report_id);
ALTER TABLE issue_links ADD CONSTRAINT ck_issue_links_one_owner
    CHECK ((test_result_id IS NULL) <> (bug_report_id IS NULL));
-- The tracker's own state name (e.g. "Active") and its category, for showing and mapping.
ALTER TABLE issue_links ADD COLUMN tracker_state VARCHAR(100);
ALTER TABLE issue_links ADD COLUMN state_category VARCHAR(20);
