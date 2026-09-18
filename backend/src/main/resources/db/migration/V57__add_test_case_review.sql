-- PRD-033: optional review before a test case becomes ACTIVE (approved). IN_REVIEW needs no
-- schema change: status is a plain VARCHAR(20).
ALTER TABLE test_cases ADD COLUMN approved_by UUID;
ALTER TABLE test_cases ADD COLUMN approved_at TIMESTAMP;
ALTER TABLE test_cases ADD COLUMN approved_version INT;

ALTER TABLE test_case_versions ADD COLUMN approved_by UUID;
ALTER TABLE test_case_versions ADD COLUMN approved_version INT;

ALTER TABLE projects ADD COLUMN review_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN reviewer_min_role VARCHAR(20) NOT NULL DEFAULT 'ADMIN';
