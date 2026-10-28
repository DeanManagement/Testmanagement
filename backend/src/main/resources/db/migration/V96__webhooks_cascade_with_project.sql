-- A project with a webhook could not be deleted: fk_webhooks_project (V34) had no cascade and
-- nothing removed the webhooks first. Its delivery rows already cascade from the webhook.
ALTER TABLE webhooks DROP CONSTRAINT fk_webhooks_project;
ALTER TABLE webhooks ADD CONSTRAINT fk_webhooks_project
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;
