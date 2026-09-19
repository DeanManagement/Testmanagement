-- Aborting a run now requires a reason (bug report efb94f3f), shown on the run like a reopen reason.
ALTER TABLE test_runs ADD COLUMN abort_reason TEXT;
