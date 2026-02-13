ALTER TABLE test_runs ADD COLUMN completed_by_id UUID;
ALTER TABLE test_runs ADD COLUMN reopen_reason VARCHAR(1000);
ALTER TABLE test_runs ADD CONSTRAINT fk_test_runs_completed_by FOREIGN KEY (completed_by_id) REFERENCES users(id);
