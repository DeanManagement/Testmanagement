CREATE INDEX idx_test_cases_project_id ON test_cases (project_id);
CREATE INDEX idx_test_steps_test_case_id ON test_steps (test_case_id);
CREATE INDEX idx_test_runs_project_id ON test_runs (project_id);
CREATE INDEX idx_test_results_test_run_id ON test_results (test_run_id);
CREATE INDEX idx_test_results_test_case_id ON test_results (test_case_id);
CREATE INDEX idx_step_results_test_result_id ON step_results (test_result_id);
CREATE INDEX idx_screenshots_step_result_id ON screenshots (step_result_id);
