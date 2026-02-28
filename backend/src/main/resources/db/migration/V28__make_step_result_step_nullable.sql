ALTER TABLE step_results ALTER COLUMN test_step_id DROP NOT NULL;
ALTER TABLE step_results DROP CONSTRAINT fk_step_results_test_step;
ALTER TABLE step_results ADD CONSTRAINT fk_step_results_test_step
    FOREIGN KEY (test_step_id) REFERENCES test_steps(id) ON DELETE SET NULL;
