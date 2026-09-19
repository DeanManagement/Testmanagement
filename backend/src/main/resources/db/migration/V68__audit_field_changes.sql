-- PRD-046: what an update changed, and which object a comment (or attachment) belongs to.
-- changes is a JSON array of {"field", "from", "to"}; TEXT rather than jsonb keeps H2 and
-- PostgreSQL on one migration, and nothing queries inside it.
ALTER TABLE audit_entries ADD COLUMN changes TEXT;
ALTER TABLE audit_entries ADD COLUMN parent_entity_type VARCHAR(50);
ALTER TABLE audit_entries ADD COLUMN parent_entity_id UUID;
CREATE INDEX idx_audit_entries_project_user ON audit_entries (project_id, user_id, created_at);
CREATE INDEX idx_audit_entries_parent ON audit_entries (parent_entity_id);
