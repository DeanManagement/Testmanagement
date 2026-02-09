CREATE TABLE project_members (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    project_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT uq_project_members_user_project UNIQUE (user_id, project_id)
);
