-- PRD-045: bug keys, the NEW intake status, resolutions, and a per-project form template.

-- A per-project, never-reused key like SPI-BUG-12, backfilled in creation order (the V24 pattern).
ALTER TABLE projects ADD COLUMN next_bug_number INT NOT NULL DEFAULT 1;
ALTER TABLE bug_reports ADD COLUMN bug_key VARCHAR(40);
UPDATE bug_reports b SET bug_key = (
    SELECT p.project_key || '-BUG-' || CAST(
        (SELECT COUNT(*) FROM bug_reports b2 WHERE b2.project_id = b.project_id AND b2.created_at <= b.created_at) AS VARCHAR
    ) FROM projects p WHERE p.id = b.project_id
);
UPDATE projects p SET next_bug_number = (SELECT COUNT(*) + 1 FROM bug_reports b WHERE b.project_id = p.id);
ALTER TABLE bug_reports ALTER COLUMN bug_key SET NOT NULL;
CREATE UNIQUE INDEX idx_bug_reports_key ON bug_reports(bug_key);

-- WONTFIX was a resolution posing as a status. Closed bugs without a recorded reason become FIXED.
ALTER TABLE bug_reports ADD COLUMN resolution VARCHAR(20);
ALTER TABLE bug_reports ADD COLUMN duplicate_of_id UUID REFERENCES bug_reports(id) ON DELETE SET NULL;
CREATE INDEX idx_bug_reports_duplicate_of ON bug_reports(duplicate_of_id);
UPDATE bug_reports SET resolution = 'WONT_FIX', status = 'CLOSED' WHERE status = 'WONTFIX';
UPDATE bug_reports SET resolution = 'FIXED' WHERE status = 'CLOSED' AND resolution IS NULL;

ALTER TABLE projects ADD COLUMN bug_template_description TEXT;
ALTER TABLE projects ADD COLUMN bug_template_steps TEXT;
ALTER TABLE projects ADD COLUMN bug_template_environment TEXT;
