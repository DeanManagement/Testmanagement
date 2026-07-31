CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    in_app BOOLEAN NOT NULL DEFAULT TRUE,
    email BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_notification_preferences UNIQUE (user_id, action)
);

CREATE INDEX idx_notification_preferences_user ON notification_preferences(user_id);
