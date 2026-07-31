CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    webhook_id UUID NOT NULL,
    event VARCHAR(30) NOT NULL,
    request_body TEXT NOT NULL,
    response_status INTEGER,
    attempt INTEGER NOT NULL,
    success BOOLEAN,
    error TEXT,
    next_attempt_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by UUID,
    updated_by UUID,
    CONSTRAINT fk_webhook_deliveries_webhook FOREIGN KEY (webhook_id) REFERENCES webhooks(id) ON DELETE CASCADE
);

CREATE INDEX idx_webhook_deliveries_webhook_created ON webhook_deliveries(webhook_id, created_at DESC);

-- Supports the retry scheduler's poll for due pending deliveries (H2-portable: no partial index).
CREATE INDEX idx_webhook_deliveries_pending ON webhook_deliveries(success, next_attempt_at);
