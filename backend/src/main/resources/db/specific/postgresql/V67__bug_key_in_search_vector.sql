-- PRD-045: bug keys (V66) are searchable like case and run keys. A generated column's expression
-- cannot be altered, so the column and its index are rebuilt. Postgres-only, like V47.
DROP INDEX idx_bug_reports_search;
ALTER TABLE bug_reports DROP COLUMN search_vector;
ALTER TABLE bug_reports ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    to_tsvector('simple',
        coalesce(bug_key, '') || ' ' || coalesce(title, '') || ' ' || coalesce(description, ''))
) STORED;
CREATE INDEX idx_bug_reports_search ON bug_reports USING GIN (search_vector);
