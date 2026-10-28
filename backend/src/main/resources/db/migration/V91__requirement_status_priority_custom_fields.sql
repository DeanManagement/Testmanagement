-- TM18 (PRD-053 §5 step 1): requirements get a lifecycle status, a priority and custom fields.
-- Existing requirements start as DRAFT / MEDIUM, the same defaults a new one gets.
ALTER TABLE requirements ADD COLUMN status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL;
ALTER TABLE requirements ADD COLUMN priority VARCHAR(20) DEFAULT 'MEDIUM' NOT NULL;

-- Requirements become the fourth custom-field owner; exactly one owner per row, as in V59.
ALTER TABLE custom_field_values ADD COLUMN requirement_id UUID REFERENCES requirements(id) ON DELETE CASCADE;
ALTER TABLE custom_field_values DROP CONSTRAINT ck_custom_field_values_one_owner;
ALTER TABLE custom_field_values ADD CONSTRAINT ck_custom_field_values_one_owner CHECK (
    (CASE WHEN test_case_id IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN test_run_id IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN bug_report_id IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN requirement_id IS NOT NULL THEN 1 ELSE 0 END) = 1);
CREATE INDEX idx_custom_field_values_requirement ON custom_field_values(requirement_id);
