ALTER TABLE users ADD COLUMN created_by UUID;
ALTER TABLE users ADD COLUMN updated_by UUID;

ALTER TABLE projects ADD COLUMN created_by UUID;
ALTER TABLE projects ADD COLUMN updated_by UUID;

ALTER TABLE project_members ADD COLUMN created_by UUID;
ALTER TABLE project_members ADD COLUMN updated_by UUID;

ALTER TABLE test_cases ADD COLUMN created_by UUID;
ALTER TABLE test_cases ADD COLUMN updated_by UUID;

ALTER TABLE test_steps ADD COLUMN created_by UUID;
ALTER TABLE test_steps ADD COLUMN updated_by UUID;

ALTER TABLE step_images ADD COLUMN created_by UUID;
ALTER TABLE step_images ADD COLUMN updated_by UUID;

ALTER TABLE test_suites ADD COLUMN created_by UUID;
ALTER TABLE test_suites ADD COLUMN updated_by UUID;

ALTER TABLE test_runs ADD COLUMN created_by UUID;
ALTER TABLE test_runs ADD COLUMN updated_by UUID;

ALTER TABLE test_results ADD COLUMN created_by UUID;
ALTER TABLE test_results ADD COLUMN updated_by UUID;

ALTER TABLE step_results ADD COLUMN created_by UUID;
ALTER TABLE step_results ADD COLUMN updated_by UUID;

ALTER TABLE screenshots ADD COLUMN created_by UUID;
ALTER TABLE screenshots ADD COLUMN updated_by UUID;

ALTER TABLE test_plans ADD COLUMN created_by UUID;
ALTER TABLE test_plans ADD COLUMN updated_by UUID;

ALTER TABLE api_keys ADD COLUMN created_by UUID;
ALTER TABLE api_keys ADD COLUMN updated_by UUID;

ALTER TABLE comments ADD COLUMN created_by UUID;
ALTER TABLE comments ADD COLUMN updated_by UUID;

ALTER TABLE audit_entries ADD COLUMN created_by UUID;
ALTER TABLE audit_entries ADD COLUMN updated_by UUID;
