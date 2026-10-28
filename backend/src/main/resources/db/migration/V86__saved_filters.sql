-- PRD-053 §3 (TM10): a list's filters are its URL query string, so saving a filter stores that string.
-- view names the list it belongs to; only the test-case list uses it so far.
CREATE TABLE saved_filters (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    view VARCHAR(50) NOT NULL,
    query_string VARCHAR(4000) NOT NULL,
    shared BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID
);
CREATE INDEX idx_saved_filters_project_view ON saved_filters (project_id, view);
CREATE INDEX idx_saved_filters_owner ON saved_filters (owner_id);
