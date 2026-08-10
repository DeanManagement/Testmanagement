-- PRD-025 §3.6: an audit trail for everything an agent does through the MCP tools. Without it,
-- "who created these 40 test cases?" has no answer beyond a service account name.
--
-- Deliberately no foreign keys. Two reasons, and both are the point of an audit table:
--   1. It must outlive what it references. Deleting an API key or a project must not erase the
--      record of what was done with it — the opposite of V39's ON DELETE CASCADE on api_keys.
--   2. Rows are written in their own transaction (REQUIRES_NEW, so a refused or failed call still
--      leaves a trace after its own transaction rolls back). A FK would then have to be satisfied
--      by data another, still-uncommitted transaction created.
-- Growth is bounded by the retention purge (app.mcp.audit-retention-days), not by cascades.
CREATE TABLE mcp_tool_invocations (
    id                  UUID PRIMARY KEY,
    api_key_id          UUID,
    project_id          UUID,
    service_user_id     UUID,
    tool_name           VARCHAR(100) NOT NULL,
    -- Truncated to 4 KB by the auditor. Arguments only; no key material is ever passed as one.
    arguments_json      TEXT,
    -- SUCCESS | REFUSED | ERROR. REFUSED is a guard doing its job, not a fault.
    outcome             VARCHAR(20) NOT NULL,
    error_message       VARCHAR(1000),
    created_entity_type VARCHAR(50),
    created_entity_id   UUID,
    duration_ms         BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    created_by          UUID,
    updated_by          UUID
);

CREATE INDEX idx_mcp_invocations_project_created ON mcp_tool_invocations (project_id, created_at DESC);
CREATE INDEX idx_mcp_invocations_api_key ON mcp_tool_invocations (api_key_id);
-- Drives the retention purge.
CREATE INDEX idx_mcp_invocations_created ON mcp_tool_invocations (created_at);
