-- TM18 (PRD-053 §5 step 2): requirements nest (Epic -> Story -> Requirement). The service moves a
-- deleted requirement's children up to its own parent and refuses cycles; SET NULL is only the
-- safety net for a delete that bypasses it.
ALTER TABLE requirements ADD COLUMN parent_id UUID REFERENCES requirements(id) ON DELETE SET NULL;
CREATE INDEX idx_requirements_parent ON requirements(parent_id);
