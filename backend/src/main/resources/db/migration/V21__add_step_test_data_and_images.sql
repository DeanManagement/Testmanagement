ALTER TABLE test_steps ADD COLUMN test_data TEXT;

CREATE TABLE step_images (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    data BYTEA NOT NULL,
    test_step_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_step_images_test_step FOREIGN KEY (test_step_id) REFERENCES test_steps(id) ON DELETE CASCADE
);
CREATE INDEX idx_step_images_test_step ON step_images(test_step_id);
