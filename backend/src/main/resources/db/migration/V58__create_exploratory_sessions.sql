-- PRD-034: exploratory testing sessions. A separate entity rather than a result-less TestRun, so
-- plan pass rates, dashboards, flakiness and run webhooks never see them.
ALTER TABLE projects ADD COLUMN next_session_number INT NOT NULL DEFAULT 1;

CREATE TABLE exploratory_sessions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    test_plan_id UUID REFERENCES test_plans(id) ON DELETE SET NULL,
    environment_id UUID REFERENCES project_environments(id) ON DELETE SET NULL,
    session_key VARCHAR(30) NOT NULL UNIQUE,
    charter TEXT NOT NULL,
    timebox_minutes INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    tester_id UUID REFERENCES users(id) ON DELETE SET NULL,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    summary TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT ck_exploratory_sessions_timebox CHECK (timebox_minutes BETWEEN 5 AND 480)
);
CREATE INDEX idx_exploratory_sessions_project ON exploratory_sessions(project_id, created_at);
CREATE INDEX idx_exploratory_sessions_plan ON exploratory_sessions(test_plan_id);
CREATE INDEX idx_exploratory_sessions_tester ON exploratory_sessions(tester_id, status);

CREATE TABLE exploratory_session_notes (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES exploratory_sessions(id) ON DELETE CASCADE,
    note_type VARCHAR(20) NOT NULL,
    body TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX idx_exploratory_session_notes_session ON exploratory_session_notes(session_id, occurred_at);

-- Same shape as screenshots (V12); a sibling table because screenshots.step_result_id is NOT NULL.
CREATE TABLE exploratory_session_note_images (
    id UUID PRIMARY KEY,
    note_id UUID NOT NULL UNIQUE REFERENCES exploratory_session_notes(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    data BYTEA NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID
);

ALTER TABLE bug_reports ADD COLUMN exploratory_session_id UUID
    REFERENCES exploratory_sessions(id) ON DELETE SET NULL;
