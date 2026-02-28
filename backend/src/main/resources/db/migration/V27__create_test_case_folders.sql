CREATE TABLE test_case_folders (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id UUID,
    project_id UUID NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_test_case_folders_parent FOREIGN KEY (parent_id) REFERENCES test_case_folders(id),
    CONSTRAINT fk_test_case_folders_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_test_case_folders_project ON test_case_folders (project_id);
CREATE INDEX idx_test_case_folders_parent ON test_case_folders (parent_id);

ALTER TABLE test_cases ADD COLUMN folder_id UUID;
ALTER TABLE test_cases ADD CONSTRAINT fk_test_cases_folder FOREIGN KEY (folder_id) REFERENCES test_case_folders(id) ON DELETE SET NULL;
CREATE INDEX idx_test_cases_folder ON test_cases (folder_id);
