-- A build server can be limited to some projects. all_projects is explicit rather than "no rows",
-- so deleting the last project a server was limited to never opens that server to everyone.
ALTER TABLE build_server_configs ADD COLUMN all_projects BOOLEAN DEFAULT TRUE NOT NULL;

CREATE TABLE build_server_projects (
    build_server_config_id UUID NOT NULL,
    project_id UUID NOT NULL,
    CONSTRAINT pk_build_server_projects PRIMARY KEY (build_server_config_id, project_id),
    CONSTRAINT fk_build_server_projects_server FOREIGN KEY (build_server_config_id)
        REFERENCES build_server_configs(id) ON DELETE CASCADE,
    CONSTRAINT fk_build_server_projects_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE
);

-- Project admins may now enable a server's workflows themselves. Limit servers that already exist
-- to the projects already using them, so upgrading hands no project another team's pipelines.
UPDATE build_server_configs SET all_projects = FALSE;
INSERT INTO build_server_projects (build_server_config_id, project_id)
SELECT DISTINCT w.build_server_config_id, a.project_id
FROM project_build_workflows a
JOIN build_workflows w ON w.id = a.build_workflow_id;
