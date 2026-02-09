CREATE TABLE screenshots (
    id UUID PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    data BYTEA NOT NULL,
    step_result_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_screenshots_step_result FOREIGN KEY (step_result_id) REFERENCES step_results(id) ON DELETE CASCADE
);
