-- TM24: projects define their own result statuses on top of the fixed categories. The category is
-- what every metric reads (pass rate, gates, flaky detection, webhooks, CI); test_results.status and
-- step_results.status keep holding it, unchanged. custom_status_id only adds the name a tester picked.
CREATE TABLE project_result_statuses (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    name_normalized VARCHAR(100) NOT NULL,
    color VARCHAR(7) NOT NULL,
    category VARCHAR(20) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_project_result_statuses_name UNIQUE (project_id, name_normalized),
    CONSTRAINT ck_project_result_statuses_category
        CHECK (category IN ('PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'))
);

ALTER TABLE test_results ADD COLUMN custom_status_id UUID;
ALTER TABLE test_results ADD CONSTRAINT fk_test_results_custom_status
    FOREIGN KEY (custom_status_id) REFERENCES project_result_statuses(id) ON DELETE SET NULL;
CREATE INDEX idx_test_results_custom_status ON test_results (custom_status_id);

ALTER TABLE step_results ADD COLUMN custom_status_id UUID;
ALTER TABLE step_results ADD CONSTRAINT fk_step_results_custom_status
    FOREIGN KEY (custom_status_id) REFERENCES project_result_statuses(id) ON DELETE SET NULL;
CREATE INDEX idx_step_results_custom_status ON step_results (custom_status_id);

-- One default per category for every existing project, so the UI always has statuses to offer.
-- New projects get the same set from ProjectResultStatusService.seedDefaults. gen_random_uuid() is
-- built into PostgreSQL 13+ and H2 2.x (see V56).
INSERT INTO project_result_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Pending', 'pending', '#64748b', 'PENDING', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_result_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Passed', 'passed', '#16a34a', 'PASSED', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_result_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Failed', 'failed', '#dc2626', 'FAILED', 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_result_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Blocked', 'blocked', '#d97706', 'BLOCKED', 3, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_result_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Skipped', 'skipped', '#475569', 'SKIPPED', 4, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
