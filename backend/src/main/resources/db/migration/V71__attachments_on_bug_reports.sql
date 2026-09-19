-- PRD-051: bug reports become the second owner of attachments. Exactly one owner per row, the
-- pattern of ck_custom_field_values_one_owner (V59).
ALTER TABLE attachments ALTER COLUMN test_case_id DROP NOT NULL;
ALTER TABLE attachments ADD COLUMN bug_report_id UUID REFERENCES bug_reports(id) ON DELETE CASCADE;
ALTER TABLE attachments ADD CONSTRAINT ck_attachments_one_owner CHECK (
    (test_case_id IS NOT NULL AND bug_report_id IS NULL) OR
    (test_case_id IS NULL AND bug_report_id IS NOT NULL));
CREATE INDEX idx_attachments_bug_report ON attachments(bug_report_id);
