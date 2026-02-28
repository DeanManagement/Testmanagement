ALTER TABLE test_plans ADD COLUMN assignee_id UUID;
ALTER TABLE test_plans ADD CONSTRAINT fk_test_plans_assignee FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE entity_watchers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_entity_watchers_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_entity_watchers_unique ON entity_watchers(user_id, entity_type, entity_id);
CREATE INDEX idx_entity_watchers_entity ON entity_watchers(entity_type, entity_id);
