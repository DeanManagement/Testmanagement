-- Postgres-only full-text search indexes (PRD-007). Applied only when the DB vendor is postgresql
-- (Flyway {vendor} location); H2 dev/test uses the LIKE fallback and skips this migration.
--
-- Numbered V47, not V38, and the version must stay unique ACROSS BOTH locations: Flyway resolves
-- db/migration and db/specific/{vendor} into one timeline, so a vendor migration reusing a version
-- from db/migration aborts startup with "Found more than one migration with version N". That is
-- exactly what a V38 here did — invisibly, because the dev profile used to load only db/migration.
-- Generated tsvector columns keep the index maintenance-free; 'simple' config is language-tolerant.

ALTER TABLE test_cases ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    to_tsvector('simple',
        coalesce(test_case_key, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, ''))
) STORED;
CREATE INDEX idx_test_cases_search ON test_cases USING GIN (search_vector);

ALTER TABLE test_runs ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    to_tsvector('simple',
        coalesce(test_run_key, '') || ' ' || coalesce(name, '') || ' ' || coalesce(environment, ''))
) STORED;
CREATE INDEX idx_test_runs_search ON test_runs USING GIN (search_vector);

ALTER TABLE bug_reports ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    to_tsvector('simple',
        coalesce(title, '') || ' ' || coalesce(description, ''))
) STORED;
CREATE INDEX idx_bug_reports_search ON bug_reports USING GIN (search_vector);

ALTER TABLE projects ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    to_tsvector('simple',
        coalesce(project_key, '') || ' ' || coalesce(name, '') || ' ' || coalesce(description, ''))
) STORED;
CREATE INDEX idx_projects_search ON projects USING GIN (search_vector);
