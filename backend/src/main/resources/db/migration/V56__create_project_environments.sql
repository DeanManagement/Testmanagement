-- PRD-032: a per-project environment catalogue. test_runs.environment and bug_reports.environment
-- stay as a denormalised copy of the name so the many read paths keep working unchanged.
CREATE TABLE project_environments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    name_normalized VARCHAR(255) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_project_environments_name UNIQUE (project_id, name_normalized)
);

ALTER TABLE test_runs ADD COLUMN environment_id UUID REFERENCES project_environments(id) ON DELETE SET NULL;
ALTER TABLE bug_reports ADD COLUMN environment_id UUID REFERENCES project_environments(id) ON DELETE SET NULL;
CREATE INDEX idx_test_runs_environment ON test_runs(environment_id);
CREATE INDEX idx_bug_reports_environment ON bug_reports(environment_id);

-- Backfill. gen_random_uuid() is built into PostgreSQL 13+ and H2 2.x, so this stays vendor-neutral.
UPDATE test_runs SET environment = NULL WHERE TRIM(environment) = '';
UPDATE bug_reports SET environment = NULL WHERE TRIM(environment) = '';

INSERT INTO project_environments (id, project_id, name, name_normalized, sort_order, archived, created_at, updated_at)
SELECT gen_random_uuid(), project_id, MIN(TRIM(environment)), LOWER(TRIM(environment)), 0, FALSE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (SELECT project_id, environment FROM test_runs WHERE environment IS NOT NULL
      UNION ALL
      SELECT project_id, environment FROM bug_reports WHERE environment IS NOT NULL) used
GROUP BY project_id, LOWER(TRIM(environment));

UPDATE test_runs SET environment_id = (
    SELECT pe.id FROM project_environments pe
    WHERE pe.project_id = test_runs.project_id AND pe.name_normalized = LOWER(TRIM(test_runs.environment)))
WHERE environment IS NOT NULL;
UPDATE bug_reports SET environment_id = (
    SELECT pe.id FROM project_environments pe
    WHERE pe.project_id = bug_reports.project_id AND pe.name_normalized = LOWER(TRIM(bug_reports.environment)))
WHERE environment IS NOT NULL;

-- Canonicalise the copies. Only case and whitespace can change.
UPDATE test_runs SET environment = (SELECT pe.name FROM project_environments pe WHERE pe.id = test_runs.environment_id)
WHERE environment_id IS NOT NULL;
UPDATE bug_reports SET environment = (SELECT pe.name FROM project_environments pe WHERE pe.id = bug_reports.environment_id)
WHERE environment_id IS NOT NULL;
