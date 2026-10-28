-- TM19 (PRD-053 §5 step 3): one-way requirement sync from the project's issue tracker, Azure DevOps
-- only for now. The connection and token are the tracker's (issue_tracker_configs); this holds what
-- to pull and how to map it. States are comma-separated names, matched ignoring case; any other
-- state maps to DRAFT.
CREATE TABLE requirement_sync_configs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    scheduled BOOLEAN NOT NULL DEFAULT FALSE,
    tracker_query TEXT NOT NULL,
    external_id_prefix VARCHAR(20),
    approved_states VARCHAR(1000) NOT NULL,
    obsolete_states VARCHAR(1000) NOT NULL,
    last_synced_at TIMESTAMP,
    last_error TEXT,
    last_error_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_requirement_sync_configs_project UNIQUE (project_id)
);
