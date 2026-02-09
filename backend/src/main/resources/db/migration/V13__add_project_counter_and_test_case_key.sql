ALTER TABLE projects ADD COLUMN next_test_case_number INT NOT NULL DEFAULT 1;
ALTER TABLE test_cases ADD COLUMN test_case_key VARCHAR(30) NOT NULL DEFAULT '';
