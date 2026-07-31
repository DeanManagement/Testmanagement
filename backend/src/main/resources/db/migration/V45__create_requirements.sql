CREATE TABLE requirements (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_requirements_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    -- External ids come from someone else's system (a spec, a ticket tracker); they are unique
    -- within a project but two projects may legitimately both have "REQ-1".
    CONSTRAINT uq_requirements_project_external UNIQUE (project_id, external_id)
);

CREATE INDEX idx_requirements_project ON requirements (project_id);

CREATE TABLE requirement_test_cases (
    requirement_id UUID NOT NULL,
    test_case_id UUID NOT NULL,
    PRIMARY KEY (requirement_id, test_case_id),
    CONSTRAINT fk_requirement_test_cases_requirement FOREIGN KEY (requirement_id) REFERENCES requirements(id) ON DELETE CASCADE,
    -- Deleting a test case removes its coverage but leaves the requirement, which then correctly
    -- shows as uncovered rather than silently vanishing from the report.
    CONSTRAINT fk_requirement_test_cases_case FOREIGN KEY (test_case_id) REFERENCES test_cases(id) ON DELETE CASCADE
);

CREATE INDEX idx_requirement_test_cases_case ON requirement_test_cases (test_case_id);
