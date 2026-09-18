-- PRD-035: per-project typed fields on test cases, test runs and bug reports. Values get one
-- nullable FK per owner (exactly one set) rather than a polymorphic (entity_type, entity_id) pair,
-- so they cascade with their owner and filter with a plain correlated EXISTS on either database.
CREATE TABLE custom_field_definitions (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    options_json TEXT,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_custom_field_definitions_name UNIQUE (project_id, entity_type, name)
);

CREATE TABLE custom_field_values (
    id UUID PRIMARY KEY,
    field_id UUID NOT NULL REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    test_case_id UUID REFERENCES test_cases(id) ON DELETE CASCADE,
    test_run_id UUID REFERENCES test_runs(id) ON DELETE CASCADE,
    bug_report_id UUID REFERENCES bug_reports(id) ON DELETE CASCADE,
    value_text VARCHAR(500),
    value_number NUMERIC(19, 4),
    value_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    -- CASE sums rather than boolean arithmetic, which H2 and PostgreSQL treat differently.
    CONSTRAINT ck_custom_field_values_one_owner CHECK (
        (CASE WHEN test_case_id IS NOT NULL THEN 1 ELSE 0 END)
      + (CASE WHEN test_run_id IS NOT NULL THEN 1 ELSE 0 END)
      + (CASE WHEN bug_report_id IS NOT NULL THEN 1 ELSE 0 END) = 1)
);
CREATE INDEX idx_custom_field_values_text ON custom_field_values(field_id, value_text);
CREATE INDEX idx_custom_field_values_number ON custom_field_values(field_id, value_number);
CREATE INDEX idx_custom_field_values_date ON custom_field_values(field_id, value_date);
CREATE INDEX idx_custom_field_values_test_case ON custom_field_values(test_case_id);
CREATE INDEX idx_custom_field_values_test_run ON custom_field_values(test_run_id);
CREATE INDEX idx_custom_field_values_bug_report ON custom_field_values(bug_report_id);

ALTER TABLE test_case_versions ADD COLUMN custom_fields_json TEXT;
