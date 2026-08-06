-- PRD-012 assumed every provider speaks OpenID Connect. GitHub does not: its user-facing sign-in
-- is plain OAuth 2.0, with no ID token and no discovery document, so identity has to be fetched
-- from its API instead of read from claims. This column says which of the two a row is.
--
-- V48, not V38: db/specific/postgresql already holds V47. Flyway merges the vendor location into
-- one timeline, so versions must be unique across both — MigrationVersionsTest enforces it.
ALTER TABLE sso_providers ADD COLUMN protocol VARCHAR(20) NOT NULL DEFAULT 'OIDC';

-- Existing rows are all OIDC by construction; the default above covers them and the column keeps
-- its default so an insert that predates this change still works.
