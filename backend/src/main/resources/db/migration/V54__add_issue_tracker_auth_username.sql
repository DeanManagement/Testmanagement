-- PRD-029 §3.1: Jira Cloud authenticates with HTTP Basic "email:apiToken", where every other tracker
-- takes a single bearer-style token. The account email gets a column of its own rather than being
-- packed into the encrypted token string, which would hide a Jira-only parsing rule inside a secret.
-- Not a secret itself; NULL for every provider that does not need it, so existing rows are unchanged.
ALTER TABLE issue_tracker_configs ADD COLUMN auth_username VARCHAR(255) NULL;
