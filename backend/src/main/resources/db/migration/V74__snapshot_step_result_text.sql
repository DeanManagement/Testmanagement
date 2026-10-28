-- A step result keeps the text of the step it executed (TM25). Replacing a case's steps deletes the
-- old rows and V28's ON DELETE SET NULL cut the link, so an open run showed those steps blank.
ALTER TABLE step_results ADD COLUMN step_action TEXT;
ALTER TABLE step_results ADD COLUMN step_expected_result TEXT;
ALTER TABLE step_results ADD COLUMN step_test_data TEXT;

-- Rows whose step still exists; those already cut off have nothing left to copy.
UPDATE step_results SET
    step_action = (SELECT ts.action FROM test_steps ts WHERE ts.id = step_results.test_step_id),
    step_expected_result = (SELECT ts.expected_result FROM test_steps ts WHERE ts.id = step_results.test_step_id),
    step_test_data = (SELECT ts.test_data FROM test_steps ts WHERE ts.id = step_results.test_step_id)
WHERE test_step_id IS NOT NULL;
