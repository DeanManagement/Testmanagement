CREATE TABLE webhooks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    url VARCHAR(2048) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_webhooks_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE INDEX idx_webhooks_project ON webhooks(project_id);

CREATE TABLE webhook_events (
    webhook_id UUID NOT NULL,
    event VARCHAR(30) NOT NULL,
    CONSTRAINT fk_webhook_events_webhook FOREIGN KEY (webhook_id) REFERENCES webhooks(id) ON DELETE CASCADE,
    CONSTRAINT uq_webhook_events UNIQUE (webhook_id, event)
);
