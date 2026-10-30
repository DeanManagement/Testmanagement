-- TM47: projects name their own bug statuses on top of the fixed categories, and may restrict which
-- status can follow which. bug_reports.status keeps holding the category every metric reads
-- (release gate, My queue, defect dashboard); custom_status_id adds the project's own status.
CREATE TABLE project_bug_statuses (
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
    CONSTRAINT uq_project_bug_statuses_name UNIQUE (project_id, name_normalized),
    CONSTRAINT ck_project_bug_statuses_category
        CHECK (category IN ('NEW', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED'))
);

ALTER TABLE bug_reports ADD COLUMN custom_status_id UUID;
ALTER TABLE bug_reports ADD CONSTRAINT fk_bug_reports_custom_status
    FOREIGN KEY (custom_status_id) REFERENCES project_bug_statuses(id) ON DELETE SET NULL;
CREATE INDEX idx_bug_reports_custom_status ON bug_reports (custom_status_id);

-- No rows for a project: any status may follow any other, as before.
CREATE TABLE bug_status_transitions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    from_status_id UUID NOT NULL REFERENCES project_bug_statuses(id) ON DELETE CASCADE,
    to_status_id UUID NOT NULL REFERENCES project_bug_statuses(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_bug_status_transitions UNIQUE (from_status_id, to_status_id)
);
CREATE INDEX idx_bug_status_transitions_project ON bug_status_transitions (project_id);

-- One default per category for every existing project (new ones: BugStatusService.seedDefaults),
-- and every existing bug on the default of its category, so no bug is without a status.
INSERT INTO project_bug_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'New', 'new', '#64748b', 'NEW', 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_bug_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Open', 'open', '#2563eb', 'OPEN', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_bug_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'In progress', 'in progress', '#d97706', 'IN_PROGRESS', 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_bug_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Resolved', 'resolved', '#16a34a', 'RESOLVED', 3, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;
INSERT INTO project_bug_statuses (id, project_id, name, name_normalized, color, category, position, archived, created_at, updated_at)
SELECT gen_random_uuid(), id, 'Closed', 'closed', '#475569', 'CLOSED', 4, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM projects;

UPDATE bug_reports SET custom_status_id = (
    SELECT s.id FROM project_bug_statuses s
    WHERE s.project_id = bug_reports.project_id AND s.category = bug_reports.status);
