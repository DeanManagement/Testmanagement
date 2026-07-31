-- PRD-020: token_version is embedded as a JWT claim and checked on every request.
-- Incrementing it invalidates all outstanding tokens (server-side logout, password change).
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
