-- PRD-025 §3.2: an API key now authenticates as a real service user holding a real project role.
-- Before this, the API-key principal was the string "api-key:<name>", which does not parse as a
-- UUID, so ProjectAccessService.currentUserId() returned null and ProjectRoleAspect failed open.
-- Backfill of service users for pre-existing keys is done in Java (ApiKeyServiceUserBackfill):
-- generating UUIDs and inserting users is not expressible vendor-neutrally across Postgres and H2.

ALTER TABLE users ADD COLUMN service_account BOOLEAN NOT NULL DEFAULT FALSE;

-- VIEWER or TESTER only. ADMIN is deliberately not offered: an agent or CI job has no business
-- managing project members or deleting a project.
ALTER TABLE api_keys ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'TESTER';

-- NULL until the backfill runs, and permanently NULL for legacy project-less keys, which have no
-- project to hold a membership on.
ALTER TABLE api_keys ADD COLUMN service_user_id UUID NULL REFERENCES users(id);
CREATE UNIQUE INDEX idx_api_keys_service_user ON api_keys (service_user_id);
