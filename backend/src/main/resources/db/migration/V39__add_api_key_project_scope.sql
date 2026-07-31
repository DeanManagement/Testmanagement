-- PRD-021 §4.2: API keys are scoped to a project. NULL = legacy/global key (deprecated,
-- still accepted with a startup warning; to be rejected in a future release).
ALTER TABLE api_keys ADD COLUMN project_id UUID NULL REFERENCES projects(id) ON DELETE CASCADE;
CREATE INDEX idx_api_keys_project ON api_keys (project_id);
