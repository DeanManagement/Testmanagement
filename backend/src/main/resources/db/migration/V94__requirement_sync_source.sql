-- TM19: a synced requirement remembers where it came from. A non-null source makes the synced
-- fields (external id, title, description, status, priority, parent) read-only locally.
ALTER TABLE requirements ADD COLUMN source VARCHAR(20);
ALTER TABLE requirements ADD COLUMN source_url VARCHAR(1000);
ALTER TABLE requirements ADD COLUMN last_synced_at TIMESTAMP;
