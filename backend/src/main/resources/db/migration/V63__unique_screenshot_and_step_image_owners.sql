-- One screenshot per step result and one image per test step, as the entities (@OneToOne) assume.
-- Neither was enforced: two concurrent uploads could both insert, after which the step result (or
-- step) could no longer be loaded at all - "more than one row" - so its whole run became unreadable
-- (bug report bd5b0f76, run SPI-Run-18). Existing duplicates keep their newest row.
-- Correlated EXISTS rather than vendor syntax, so it runs on PostgreSQL and H2 alike. The old plain
-- indexes stay: H2 will not drop an index its foreign key uses, and a redundant index costs little.

DELETE FROM screenshots WHERE EXISTS (
    SELECT 1 FROM screenshots newer
    WHERE newer.step_result_id = screenshots.step_result_id
      AND (newer.created_at > screenshots.created_at
           OR (newer.created_at = screenshots.created_at AND newer.id > screenshots.id)));
ALTER TABLE screenshots ADD CONSTRAINT uq_screenshots_step_result UNIQUE (step_result_id);

DELETE FROM step_images WHERE EXISTS (
    SELECT 1 FROM step_images newer
    WHERE newer.test_step_id = step_images.test_step_id
      AND (newer.created_at > step_images.created_at
           OR (newer.created_at = step_images.created_at AND newer.id > step_images.id)));
ALTER TABLE step_images ADD CONSTRAINT uq_step_images_test_step UNIQUE (test_step_id);
